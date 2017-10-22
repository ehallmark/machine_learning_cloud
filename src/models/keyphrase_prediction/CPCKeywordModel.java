package models.keyphrase_prediction;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.models.TimeDensityModel;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.stages.*;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class CPCKeywordModel {
    public static final boolean debug = false;


    public static void main(String[] args) {
        runModel();
    }

    public static void runModel() {
        int cpcLength = 8;
        List<String> CPCs = Database.getClassCodes().parallelStream().map(c->ClassCodeHandler.convertToLabelFormat(c)).map(c->c.length()>cpcLength?c.substring(0,cpcLength).trim():c).distinct().collect(Collectors.toList());
        Map<String,String> cpcToRawTitleMap = Database.getClassCodeToClassTitleMap();
        Map<String,Collection<MultiStem>> cpcToTitleMap = new HashMap<>();

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        cpcToRawTitleMap.entrySet().parallelStream().forEach(e->{
            Annotation doc = new Annotation(e.getValue().toUpperCase());
            pipeline.annotate(doc, d -> {
                List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
                Set<MultiStem> appeared = new HashSet<>();
                for(CoreMap sentence: sentences) {
                    // traversing the words in the current sentence
                    // a CoreLabel is a CoreMap with additional token-specific methods
                    String prevStem = null;
                    String prevPrevStem = null;
                    String prevWord = null;
                    String prevPrevWord = null;
                    for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                        // this is the text of the token
                        String word = token.get(CoreAnnotations.TextAnnotation.class);
                        boolean valid = true;
                        for(int i = 0; i < word.length(); i++) {
                            if(!Character.isAlphabetic(word.charAt(i))) {
                                valid = false;
                            }
                        }
                        if(!valid) continue;

                        // could be the stem
                        String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                        if(Constants.STOP_WORD_SET.contains(lemma)||Constants.STOP_WORD_SET.contains(word)) {
                            prevPrevStem=null;
                            prevStem=null;
                            prevWord=null;
                            prevPrevWord=null;
                            continue;
                        }

                        try {
                            String stem = new Stemmer().stem(lemma);
                            if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                                // this is the POS tag of the token
                                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                                if (Stage.validPOS.contains(pos)) {
                                    // don't want to end in adjectives (nor past tense verb)
                                    if (!Stage.adjectivesPOS.contains(pos) && !pos.equals("VBD") && !((!pos.startsWith("N"))&&(word.endsWith("ing")||word.endsWith("ed")))) {
                                        appeared.add(new MultiStem(new String[]{stem},-1);
                                        if (prevStem != null) {
                                            appeared.add(new MultiStem(new String[]{prevStem, stem}, -1));
                                            if (prevPrevStem != null) {
                                                appeared.add(new MultiStem(new String[]{prevPrevStem, prevStem, stem}, -1));
                                            }
                                        }
                                    }
                                } else {
                                    stem = null;
                                }
                            } else {
                                stem = null;
                            }
                            prevPrevStem = prevStem;
                            prevStem = stem;
                            prevPrevWord = prevWord;
                            prevWord = word;

                        } catch(Exception e2) {
                            System.out.println("Error while stemming: "+lemma);
                            prevStem = null;
                            prevPrevStem = null;
                            prevWord=null;
                            prevPrevWord=null;
                        }
                    }
                }


            });

            return null;
        };

        RadixTree<Collection<MultiStem>> titlesTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
        cpcToTitleMap.entrySet().parallelStream().forEach(e->{
            titlesTrie.put(e.getKey(),e.getValue());
        });

        AtomicInteger missing = new AtomicInteger(0);
        CPCs.forEach(cpc->{
            if(!titlesTrie.getKeysStartingWith(ClassCodeHandler.convertToLabelFormat(cpc)).iterator().hasNext()) {
                missing.getAndIncrement();
            }
        });
        System.out.println("Missing: "+missing.get());
        List<MultiStem> allWords = cpcToTitleMap.values().parallelStream().flatMap(t-> t.stream()).distinct().collect(Collectors.toList());
        KeywordModelRunner.reindex(allWords);

        Map<String,Integer> cpcToIdx = IntStream.range(0,CPCs.size()).mapToObj(i->new Pair<>(CPCs.get(i),i)).collect(Collectors.toMap(p->p._1, p->p._2));
        Map<MultiStem,Integer> wordToIdx = allWords.parallelStream().map(w->new Pair<>(w,w.getIndex())).collect(Collectors.toMap(p->p._1, p->p._2));

        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(allWords.size(),CPCs.size());
        CPCs.forEach(cpc->{
           int cpcIdx = cpcToIdx.get(cpc);
           titlesTrie.getValuesForKeysStartingWith(cpc).forEach(title->{
               for(MultiStem word : title) {
                   matrix.addToEntry(word.getIndex(), cpcIdx, 1d);
               }
           });
        });

        System.out.println("Stems before: "+allWords.size());
        Map<MultiStem,Double> stemMap = new TechnologyScorer().scoreKeywords(allWords,matrix);
        System.out.println("Found: "+stemMap.size());

        stemMap.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(30).forEach(word->{
            System.out.println("BEST: "+word.getKey());
        });
    }

}
