package models.keyphrase_prediction;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
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
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();
        RadixTree<String> titlesTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
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
        List<String> allWords = cpcToTitleMap.values().parallelStream().flatMap(t-> Stream.of(t.split("\\s+"))).distinct().collect(Collectors.toList());

        Map<String,Integer> cpcToIdx = IntStream.range(0,CPCs.size()).mapToObj(i->new Pair<>(CPCs.get(i),i)).collect(Collectors.toMap(p->p._1, p->p._2));
        Map<String,Integer> wordToIdx = IntStream.range(0,allWords.size()).mapToObj(i->new Pair<>(allWords.get(i),i)).collect(Collectors.toMap(p->p._1, p->p._2));

        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(allWords.size(),CPCs.size());
        CPCs.forEach(cpc->{
           int cpcIdx = cpcToIdx.get(cpc);
           titlesTrie.getValuesForKeysStartingWith(cpc).forEach(title->{
               for(String word : title.split("\\s+")) {
                   matrix.addToEntry(wordToIdx.get(word), cpcIdx, 1d);
               }
           });
        });

        Collection<MultiStem> stems = allWords.stream().map(word->new MultiStem(new String[]{word},wordToIdx.get(word))).collect(Collectors.toList());
        System.out.println("Stems before: "+stems.size());
        Map<MultiStem,Double> stemMap = new TechnologyScorer().scoreKeywords(stems,matrix);
        System.out.println("Found: "+stemMap.size());

        stemMap.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(30).forEach(word->{
            System.out.println("BEST: "+word.getKey());
        });
    }

}
