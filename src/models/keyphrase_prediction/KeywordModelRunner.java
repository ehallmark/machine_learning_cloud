package models.keyphrase_prediction;

import com.google.gson.Gson;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.scorers.KeywordScorer;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.apache.lucene.analysis.util.StemmerUtil;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = true;
    private static final Stemmer stemmer = new Stemmer();
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    public static void main(String[] args) {
        final long Kw = 5000;
        final int k1 = 10;
        final int k2 = 4;


        Collection<MultiStem> keywords = buildVocabulary(2010);

        // apply filter 1
        INDArray F = null; //buildFMatrix(keywords);
        applyFilters(new UnithoodScorer(), F, keywords, Kw * k1, 0, Double.MAX_VALUE);

        // apply filter 2
        INDArray M = null;
        applyFilters(new TermhoodScorer(), M, keywords, Kw * k2, 0, Double.MAX_VALUE);

        // apply filter 3
        INDArray T = null;
        applyFilters(new TechnologyScorer(), T, keywords, Kw, 0, Double.MAX_VALUE);
    }

    private static Collection<MultiStem> applyFilters(KeywordScorer scorer, INDArray matrix, Collection<MultiStem> keywords, long targetNumToKeep, double minThreshold, double maxThreshold) {
        return scorer.scoreKeywords(keywords,matrix).entrySet().stream().filter(e->e.getValue()>=minThreshold&&e.getValue()<=maxThreshold).sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(targetNumToKeep)
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    return e.getKey();
                })
                .collect(Collectors.toList());
    }

    private static Collection<MultiStem> buildVocabulary(int year) {
        LocalDate dateMin = LocalDate.of(year,1,1);
        LocalDate dateMax = dateMin.plusYears(1);
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                //.addSort(Constants.FILING_DATE, SortOrder.ASC)
                .setScroll(new TimeValue(60000))
                .setFrom(0)
                .setSize(1000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString()),true).innerHit(
                        new InnerHitBuilder().setSize(1).setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE}, new String[]{}))
                ));
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Collection<MultiStem> multiStems = Collections.synchronizedCollection(new HashSet<>());

        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ",Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");


            Annotation doc = new Annotation(text);

            synchronized (pipeline) {
                pipeline.annotate(doc,d->{
                    //System.out.println("Text: "+text);
                    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
                    for(CoreMap sentence: sentences) {
                        // traversing the words in the current sentence
                        // a CoreLabel is a CoreMap with additional token-specific methods
                        String prevWord = null;
                        String prevPrevWord = null;
                        for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                            // this is the text of the token
                            String word = token.get(CoreAnnotations.TextAnnotation.class);
                            // could be the stem
                            String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                            if(Constants.STOP_WORD_SET.contains(lemma)||Constants.STOP_WORD_SET.contains(word)) {
                                prevPrevWord=null;
                                prevWord=null;
                                continue;
                            }

                            try {
                                String stem = stemmer.stem(lemma);
                                if (stem.length() > 0 && !Constants.STOP_WORD_SET.contains(word)) {
                                    // this is the POS tag of the token
                                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                                    if (validPOS.contains(pos)) {
                                        multiStemChecker(new String[]{stem},multiStems);
                                        if(prevWord != null) {
                                            multiStemChecker(new String[]{prevWord,stem},multiStems);
                                            if(prevPrevWord != null) {
                                                multiStemChecker(new String[]{prevPrevWord,prevWord,stem},multiStems);
                                            }
                                        }
                                    } else {
                                        stem = null;
                                    }
                                } else {
                                    stem = null;
                                }
                                prevPrevWord = prevWord;
                                prevWord = stem;

                            } catch(Exception e) {
                                System.out.println("Error while stemming: "+lemma);
                                prevWord = null;
                                prevPrevWord = null;
                            }
                        }
                    }
                });
            }

            return null;
        };

        DataSearcher.iterateOverSearchResults(response, transformer, 5, false);
        return multiStems;
    }

    private static void multiStemChecker(String[] stems, Collection<MultiStem> multiStems) {
        MultiStem multiStem = new MultiStem(stems, multiStems.size());
        synchronized (multiStems) {
            if (!multiStems.contains(multiStem)) {
                if (debug) System.out.println("Adding word "+multiStem.index+": " + multiStem);
                multiStems.add(multiStem);
            }
        }
    }

}
