package models.keyphrase_prediction;

import com.google.common.util.concurrent.AtomicDouble;
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
import models.keyphrase_prediction.scorers.TermhoodScorer;
import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.linear.RealMatrix;
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
import java.util.concurrent.atomic.AtomicLong;
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
        Map<String,Collection<MultiStem>> cpcToTitleMap = Collections.synchronizedMap(new HashMap<>());
        Stage1 stage1 = new Stage1(new TimeDensityModel(),1);

        Function<Map.Entry<String,String>,Item> transformer = hit -> {
            String text = hit.getValue().toLowerCase();
            Item item = new Item(hit.getKey());
            item.addData(Stage.ASSET_ID,hit.getKey());
            item.addData(Stage.TEXT,text);
            return item;
        };

        Function<Function,Void> transformerRunner = v -> {
            new HashMap<>(cpcToRawTitleMap).entrySet().parallelStream().forEach(e->{
                v.apply(e);
            });
            return null;
        };

        Function<Function<Map<String,Object>,Void>,Void> function = attrFunction -> {
            stage1.runSamplingIterator(transformer, transformerRunner, attrFunction);
            return null;
        };

        Map<MultiStem,AtomicDouble> docCounts = Collections.synchronizedMap(new HashMap<>());
        stage1.buildVocabularyCounts(function,attributes->{
            cpcToTitleMap.put((String)attributes.get(Stage.ASSET_ID),(Collection<MultiStem>)attributes.get(Stage.APPEARED));
            Map<MultiStem,AtomicInteger> appeared = (Map<MultiStem,AtomicInteger>)attributes.get(Stage.APPEARED_WITH_COUNTS);
            appeared.entrySet().forEach(e->{
                MultiStem stem = e.getKey();
                double val = 1d/Math.log(stem.getLength());
                docCounts.putIfAbsent(stem,new AtomicDouble(0));
                docCounts.get(stem).getAndAdd(val);
            });
            return null;
        });

        Map<MultiStem,AtomicLong> wordToDocCounter = stage1.get();
        Map<MultiStem,MultiStem> selfMap = stage1.get().keySet().parallelStream().collect(Collectors.toMap(e->e,e->e));

        System.out.println("Vocab size: "+wordToDocCounter.size());

        Map<String,Collection<MultiStem>> cpcToFullTitleMap = Collections.synchronizedMap(new HashMap<>());
        RadixTree<Collection<MultiStem>> prefixTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
        cpcToTitleMap.entrySet().parallelStream().forEach(e->{
            prefixTrie.put(ClassCodeHandler.convertToLabelFormat(e.getKey()),e.getValue());
        });

        final int preBuffer = 4;
        final int postBuffer = 2;
        CPCs.parallelStream().forEach(cpc->{
           Collection<MultiStem> texts = new HashSet<>();
           for(int i = cpc.length()-preBuffer; i < cpc.length(); i++) {
               Collection<MultiStem> stems = prefixTrie.getValueForExactKey(cpc.substring(0,i));
               if(stems!=null) {
                   texts.addAll(stems);
               }
           }
           prefixTrie.getKeyValuePairsForKeysStartingWith(cpc).forEach(pair->{
               if(pair.getKey().length() <= cpc.length()+postBuffer) {
                   texts.addAll(pair.getValue());
               }
           });

           if(texts.size()>0) {
               cpcToFullTitleMap.put(cpc, texts);
           }
        });

        Collection<MultiStem> allWords = cpcToFullTitleMap.values().parallelStream().flatMap(t-> t.stream()).distinct().collect(Collectors.toList());
        KeywordModelRunner.reindex(allWords);
        System.out.println("Starting to build M matrix...");
        Function<Function<Map<String,Object>,Void>,Void> stage3Function = attrFunction -> {
            stage1.runSamplingIterator(transformer, transformerRunner, attrFunction);
            return null;
        };
        Stage3 stage3 = new Stage3(allWords,new TimeDensityModel());
        System.out.println("Stems before M: "+allWords.size());
        RealMatrix M = stage3.buildMMatrix(allWords,selfMap,stage3Function);
        allWords = Stage.applyFilters(new TermhoodScorer(), M, allWords,0.5,1d,0.1);
        System.out.println("Finished M matrix");
        System.out.println("Stems after M: "+allWords.size());

        KeywordModelRunner.reindex(allWords);
        Map<String,Integer> cpcToIdx = IntStream.range(0,CPCs.size()).mapToObj(i->new Pair<>(CPCs.get(i),i)).collect(Collectors.toMap(p->p._1, p->p._2));
        Map<MultiStem,Integer> wordToIdx = allWords.parallelStream().collect(Collectors.toMap(word->word,word->word.getIndex()));
        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(allWords.size(),CPCs.size());

        cpcToFullTitleMap.entrySet().parallelStream().forEach(e-> {
            if (e.getValue().isEmpty()) return;
            // pick the one with best tfidf
            e.getValue().forEach(word -> {
                double tf = 1d;
                double idf = Math.log(cpcToTitleMap.size() / (docCounts.getOrDefault(word, new AtomicDouble(1)).get()));
                double u = Math.log(1 + word.getStems().length);
                word.setScore((float) (u * tf * idf));
            });
            Set<MultiStem> temp = new HashSet<>();
            temp.addAll(e.getValue().stream().sorted((s1, s2) -> Float.compare(s2.getScore(), s1.getScore())).limit(5).collect(Collectors.toList()));
            e.getValue().clear();
            e.getValue().addAll(temp.stream().map(m -> selfMap.get(m)).collect(Collectors.toList()));
            if (e.getValue().size() > 0) {
                int cpcIdx = cpcToIdx.get(e.getKey());
                System.out.println("CPC " + e.getKey() + ": " + String.join("; ", e.getValue().stream().map(m -> m.getBestPhrase()).collect(Collectors.toList())));
                e.getValue().forEach(word->{
                    matrix.addToEntry(wordToIdx.get(word),cpcIdx,1d);
                });
            }
        });

        System.out.println("Stems before: "+allWords.size());
        Collection<MultiStem> stemMap = Stage.applyFilters(new TechnologyScorer(), matrix, allWords, 0.5,1d,0.1);
        stemMap.forEach(stem->{
            System.out.println("Good stem: "+selfMap.get(stem).getBestPhrase());
        });
        System.out.println("Stems after: "+stemMap.size());
    }

}
