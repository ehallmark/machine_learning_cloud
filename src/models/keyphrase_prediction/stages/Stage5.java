package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import models.classification_models.WIPOHelper;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import org.apache.commons.math3.linear.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import seeding.Constants;
import seeding.Database;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/13/17.
 */
public class Stage5 extends Stage<Map<String,List<String>>> {
    private static final boolean debug = false;
    private Map<MultiStem,MultiStem> multiStemToSelfMap;
    private Map<MultiStem,AtomicLong> multiStemToDocumentCountMap;
    private AtomicInteger notFoundCounter = new AtomicInteger(0);
    private AtomicInteger cnt = new AtomicInteger(0);
    public Stage5(Stage1 stage1, Set<MultiStem> multiStems, Model model) {
        super(model);
        KeywordModelRunner.reindex(multiStems);
        multiStemToDocumentCountMap = stage1.get();
        multiStemToSelfMap=multiStems.stream().collect(Collectors.toMap(e->e,e->e));
        this.sampling=-1;
    }

    @Override
    public Map<String, List<String>> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // run model
            runModel();
            Database.saveObject(data, getFile());
            // print sample
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(getFile().getAbsolutePath() + ".csv")))) {
                writer.write("Asset,Technologies\n");
                data.entrySet().stream().limit(10000).forEach(e -> {
                    try {
                        writer.write(e.getKey() + "," + String.join("; ", e.getValue()) + "\n");
                    } catch (Exception _e) {
                        _e.printStackTrace();
                    }
                });
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                loadData();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private void runModel() {
        data = Collections.synchronizedMap(new HashMap<>());
        Function<Map<String,Object>,Void> attributesFunction = map-> {
            String asset = map.get(ASSET_ID).toString();
            Map<MultiStem,AtomicInteger> documentStems = (Map<MultiStem,AtomicInteger>)map.get(APPEARED_WITH_COUNTS);

            Pair<MultiStem,Double> result = documentStems.entrySet().parallelStream().map(e->{
                MultiStem stem = multiStemToSelfMap.get(e.getKey());
                if(stem==null)return null;
                double df = multiStemToDocumentCountMap.getOrDefault(e.getKey(),new AtomicLong(0)).get();
                double tf = documentStems.getOrDefault(e.getKey(),new AtomicInteger(0)).get();
                double score = tf / Math.log(Math.E+df);
                return new Pair<>(stem,score);
            }).filter(s->s!=null).reduce((p1,p2)->{
                if(p1._2>p2._2) return p1;
                else return p2;
            }).orElse(null);

            List<String> technologies = null;
            if(result!=null) {
                technologies = Arrays.asList(result._1.getBestPhrase());
                data.put(asset,technologies);
                if (debug)
                    System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
            }

            if(technologies==null) {
                notFoundCounter.getAndIncrement();
                if(notFoundCounter.get()%10000==9999) {
                    System.out.println("Missing technologies for: "+notFoundCounter.get());
                }
            } else {
                if(technologies!=null&&cnt.getAndIncrement()%10000==9999) {
                    System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
                }
            }
            return null;
        };

        runSamplingIterator(attributesFunction);
    }
}
