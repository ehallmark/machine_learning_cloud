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
    private Collection<MultiStem> multiStems;
    public Stage5(Stage1 stage1, Set<MultiStem> multiStems, Model model) {
        super(model);
        this.multiStems=multiStems;
        multiStemToDocumentCountMap = stage1.get();
        multiStemToSelfMap=multiStemToDocumentCountMap.keySet().parallelStream().collect(Collectors.toMap(e->e,e->e));
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
        Collection<MultiStem> allStems = new HashSet<>(multiStemToDocumentCountMap.keySet());
        SparseRealMatrix matrix = new OpenMapBigRealMatrix(allStems.size(),multiStems.size());
        KeywordModelRunner.reindex(allStems);
        AtomicInteger idx = new AtomicInteger(0);
        Map<MultiStem,Integer> importantToIndex = multiStems.parallelStream().collect(Collectors.toMap(m->m,m->idx.getAndIncrement()));
        Map<Integer,MultiStem> indexToImportant = importantToIndex.entrySet().parallelStream().collect(Collectors.toMap(e->e.getValue(),e->e.getKey()));

        Function<Map<String,Object>,Void> cooccurrenceFunction = attributes -> {
            Map<MultiStem,AtomicInteger> appeared = (Map<MultiStem,AtomicInteger>)attributes.get(APPEARED_WITH_COUNTS);
            Map<MultiStem,AtomicInteger> allCooocurrences = appeared.entrySet().stream().filter(e->multiStemToSelfMap.containsKey(e.getKey())).collect(Collectors.toMap(e->multiStemToSelfMap.get(e.getKey()),e->e.getValue()));
            Map<MultiStem,AtomicInteger> importantCoocurrences = allCooocurrences.entrySet().stream().filter(e->multiStems.contains(e.getKey())).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

            if(debug)
                System.out.println("Num coocurrences: "+importantCoocurrences.size());

            // Unavoidable n-squared part
            allCooocurrences.entrySet().forEach(s1->{
                importantCoocurrences.entrySet().forEach(s2->{
                    matrix.addToEntry(importantToIndex.get(s1.getKey()),s2.getKey().getIndex(),(double) s1.getValue().get()*s2.getValue().get());
                });
            });
            return null;
        };
        runSamplingIterator(cooccurrenceFunction);

        // turn of sampling
        this.sampling=-1;

        data = Collections.synchronizedMap(new HashMap<>());
        Function<Map<String,Object>,Void> attributesFunction = map-> {
            String asset = map.get(ASSET_ID).toString();
            Map<MultiStem,AtomicInteger> documentStems = (Map<MultiStem,AtomicInteger>)map.get(APPEARED_WITH_COUNTS);

            RealVector result = documentStems.entrySet().parallelStream().map(e->{
                MultiStem stem = multiStemToSelfMap.get(e.getKey());
                if(stem==null)return null;

                double df = multiStemToDocumentCountMap.getOrDefault(e.getKey(),new AtomicLong(0)).get();
                double tf = e.getValue().get();
                double score = tf / Math.log(Math.E+df);

                RealVector vector = matrix.getRowVector(stem.getIndex()).mapMultiply(score);

                return vector;
            }).filter(s->s!=null).reduce((p1,p2)->{
                return p1.add(p2);
            }).orElse(null);

            List<String> technologies = null;
            if(result!=null) {
                int maxIndex = result.getMaxIndex();
                if(maxIndex >= 0) {
                    technologies = Arrays.asList(indexToImportant.get(maxIndex).getBestPhrase());
                }
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
