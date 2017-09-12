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
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    public static final File keywordCountsFile = new File("data/keyword_model_counts_2010.jobj");
    private static final File stage2File = new File("data/keyword_model_keywords_set_stage2.jobj");
    public static void main(String[] args) {
        final long Kw = 5000;
        final int k1 = 20;
        final int k2 = 5;
        final int minTokenFrequency = 50;
        final int maxTokenFrequency = 100000;

        boolean stage1 = false;
        boolean stage2 = true;

        Map<MultiStem, AtomicLong> keywordsCounts;
        if(stage1) {
            keywordsCounts = buildVocabularyCounts(2010);
            Database.trySaveObject(keywordsCounts, keywordCountsFile);
        } else {
            keywordsCounts = (Map<MultiStem,AtomicLong>)Database.loadObject(keywordCountsFile);
        }

        Collection<MultiStem> keywords;
        if(stage2) {
            // filter outliers
            keywordsCounts = truncateBetweenLengths(keywordsCounts, minTokenFrequency, maxTokenFrequency);

            keywords = new HashSet<>(keywordsCounts.keySet());

            // apply filter 1
            INDArray F = buildFMatrix(keywordsCounts);
            keywords = applyFilters(new UnithoodScorer(), F, keywords, Kw * k1, 0, Double.MAX_VALUE);
            Database.saveObject(keywords, stage2File);
            // write to csv for records
            writeToCSV(keywords,new File("data/keyword_model_stage2.csv"));
        } else {
            keywords = (Collection<MultiStem>)Database.loadObject(stage2File);
        }

        // apply filter 2
        INDArray M = buildMMatrix(keywords);
        applyFilters(new TermhoodScorer(), M, keywords, Kw * k2, 0, Double.MAX_VALUE);

        // apply filter 3
        INDArray T = null;
        applyFilters(new TechnologyScorer(), T, keywords, Kw, 0, Double.MAX_VALUE);
    }

    private static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
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

    private static INDArray buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        INDArray array = Nd4j.create(multiStemMap.size());
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array.putScalar(e.getKey().getIndex(),e.getValue().get());
        });
        return array;
    }

    private static Map<MultiStem,AtomicLong> truncateBetweenLengths(Map<MultiStem,AtomicLong> stemMap, int min, int max) {
        return stemMap.entrySet().parallelStream().filter(e->e.getValue().get()>=min&&e.getValue().get()<=max).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
    }

    private static INDArray buildMMatrix(Collection<MultiStem> multiStems) {
        // search elasticsearch and create co-occurrrence statistics
        


        return null;
    }

    private static Map<MultiStem,AtomicLong> buildVocabularyCounts(int year) {
        LocalDate dateMin = LocalDate.of(year,1,1);
        LocalDate dateMax = dateMin.plusYears(1);
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                //.addSort(Constants.FILING_DATE, SortOrder.ASC)
                .setScroll(new TimeValue(60000))
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, QueryBuilders.boolQuery().filter(QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString())),true).innerHit(
                        new InnerHitBuilder().setSize(1).setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE}, new String[]{}))
                ));
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Map<MultiStem,AtomicLong> multiStemMap = Collections.synchronizedMap(new HashMap<>());

        AtomicLong cnt = new AtomicLong(0);
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ",Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");

            Annotation doc = new Annotation(text);
            if(cnt.getAndIncrement() % 10000 == 9999) {
                System.out.println("Num distinct multistems: "+multiStemMap.size());
            }
            pipeline.annotate(doc, d -> {
                if(debug) System.out.println("Text: "+text);
                List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
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
                            String stem = new Stemmer().stem(lemma);
                            if (stem.length() > 0 && !Constants.STOP_WORD_SET.contains(word)) {
                                // this is the POS tag of the token
                                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                                if (validPOS.contains(pos)) {
                                    multiStemChecker(new String[]{stem},multiStemMap);
                                    if(prevWord != null) {
                                        multiStemChecker(new String[]{prevWord,stem},multiStemMap);
                                        if(prevPrevWord != null) {
                                            multiStemChecker(new String[]{prevPrevWord,prevWord,stem},multiStemMap);
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

            return null;
        };

        DataSearcher.iterateOverSearchResults(response, transformer, 5, false);
        return multiStemMap;
    }

    private static void multiStemChecker(String[] stems, Map<MultiStem,AtomicLong> multiStems) {
        MultiStem multiStem = new MultiStem(stems, multiStems.size());
        synchronized (multiStems) {
            AtomicLong currentCount = multiStems.get(multiStem);
            if(currentCount == null) {
                if (debug) System.out.println("Adding word " + multiStem.index + ": " + multiStem);
                multiStems.put(multiStem, new AtomicLong(1L));
            } else {
                currentCount.getAndIncrement();
            }
        }
    }

    private static void writeToCSV(Collection<MultiStem> multiStems, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Key Phrase, Score\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+","+e.index+"\n");
                }catch(Exception e2) {
                    e2.printStackTrace();
                }
            });
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
