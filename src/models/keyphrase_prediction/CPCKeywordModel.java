package models.keyphrase_prediction;

import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import cpc_normalization.CPC;
import cpc_normalization.CPCCleaner;
import cpc_normalization.CPCHierarchy;
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
        Map<String,String> cpcToRawTitleMap = Database.getClassCodeToClassTitleMap();
        List<String> CPCs = Collections.synchronizedList(new ArrayList<>(cpcToRawTitleMap.keySet()));
        Map<String,Collection<MultiStem>> cpcToTitleMap = Collections.synchronizedMap(new HashMap<>());
        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();

        int CPC_DEPTH = 3;
        Collection<CPC> mainGroup = CPCCleaner.getCPCsAtDepth(cpcHierarchy.getTopLevel(),CPC_DEPTH);

        System.out.println("Num group level cpcs: "+mainGroup.size());

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
            String cpcLabel = (String)attributes.get(Stage.ASSET_ID);
            CPC cpc = cpcHierarchy.getLabelToCPCMap().get(cpcLabel);
            cpcToTitleMap.put(cpcLabel,(Collection<MultiStem>)attributes.get(Stage.APPEARED));
            Map<MultiStem,AtomicInteger> appeared = (Map<MultiStem,AtomicInteger>)attributes.get(Stage.APPEARED_WITH_COUNTS);
            double val = 1d;
            appeared.entrySet().forEach(e->{
                MultiStem stem = e.getKey();
                docCounts.putIfAbsent(stem,new AtomicDouble(0));
                docCounts.get(stem).getAndAdd(val);
            });
            return null;
        });

        Map<MultiStem,AtomicLong> wordToDocCounter = stage1.get();
        Map<MultiStem,MultiStem> selfMap = stage1.get().keySet().parallelStream().collect(Collectors.toMap(e->e,e->e));

        System.out.println("Vocab size: "+wordToDocCounter.size());

        cpcToTitleMap.entrySet().parallelStream().forEach(e->{
            CPC cpc = cpcHierarchy.getLabelToCPCMap().get(e.getKey());
            cpc.setKeywords(Collections.synchronizedSet(new HashSet<>(e.getValue())));
        });

        AtomicInteger cnt = new AtomicInteger(0);
        cpcHierarchy.getLabelToCPCMap().values().parallelStream().forEach(cpc-> {
            if (cpc.getKeywords()==null||cpc.getKeywords().isEmpty()) return;
            // pick the one with best tfidf
            cpc.getKeywords().forEach(word -> {
                double docCount = docCounts.getOrDefault(word, new AtomicDouble(1)).get();
                double tf = 1d;
                double idf = Math.log(cpcToTitleMap.size() / (docCount));
                double u = word.getStems().length;
                double l = word.toString().length();
                double score = tf * idf * u * u * l;
                if(word.getStems().length>1) {
                    double denom = Stream.of(word.getStems()).mapToDouble(stem->docCounts.getOrDefault(new MultiStem(new String[]{stem},-1),new AtomicDouble(1d)).get()).average().getAsDouble();
                    score *= docCount/Math.sqrt(denom);
                }
                word.setScore((float) score);
            });
            Set<MultiStem> temp = new HashSet<>();
            temp.addAll(cpc.getKeywords().stream().sorted((s1, s2) -> Float.compare(s2.getScore(), s1.getScore())).limit(5).collect(Collectors.toList()));
            cpc.getKeywords().clear();
            cpc.getKeywords().addAll(temp.stream().map(m -> selfMap.get(m)).collect(Collectors.toList()));
            if (cnt.getAndIncrement()%10000==9999) {
                System.out.println(""+cnt.get()+" / "+cpcHierarchy.getLabelToCPCMap().size());
            }
        });

        Set<MultiStem> stems = mainGroup.parallelStream().filter(cpc->cpc.getKeywords()!=null).flatMap(cpc->cpc.getKeywords().stream()).collect(Collectors.toSet());
        Stage5 stage5 = new Stage5(stage1, stems, new TimeDensityModel());
        stage5.run(true);
    }

}
