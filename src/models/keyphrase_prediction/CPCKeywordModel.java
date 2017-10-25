package models.keyphrase_prediction;


import cpc_normalization.CPC;
import cpc_normalization.CPCCleaner;
import cpc_normalization.CPCHierarchy;

import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.models.TimeDensityModel;

import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SparseRealMatrix;

import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class CPCKeywordModel extends Stage<Map<MultiStem,Set<CPC>>> {
    public static final boolean debug = false;
    private static final File modelFile = new File(Constants.DATA_FOLDER+"cpc_keyword_model.jobj");

    public CPCKeywordModel() {
        super(new TimeDensityModel(),-1);
    }

    public static void main(String[] args) {
        Map<MultiStem,Set<CPC>> technologyMap = new CPCKeywordModel().run(true);
        System.out.println("Tech map size: "+technologyMap.size());
        Database.trySaveObject(technologyMap,modelFile);
    }

    @Override
    public Map<MultiStem,Set<CPC>> run(boolean _t) {
        Map<String,String> cpcToRawTitleMap = Database.getClassCodeToClassTitleMap();
        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();

        for(int CPC_DEPTH = 1; CPC_DEPTH <= 4; CPC_DEPTH++) {
            System.out.println("STARTING CPC DEPTH: "+CPC_DEPTH);
            Collection<CPC> mainGroup = CPCCleaner.getCPCsAtDepth(cpcHierarchy.getTopLevel(), CPC_DEPTH);
            mainGroup.forEach(cpc->{
                cpcHierarchy.getLabelToCPCMap().put(cpc.getName(),cpc);
            });

            System.out.println("Num group level cpcs: " + mainGroup.size());

            Stage1 stage1 = new Stage1(new TimeDensityModel(),1,year);

            Function<Pair<CPC, String>, Item> transformer = hit -> {
                String text = hit._2.toLowerCase();
                Item item = new Item(hit._1.getName());
                item.addData(Stage.ASSET_ID, hit._1.getName());
                item.addData(Stage.TEXT, text);
                item.addData("CPC", hit._1);
                return item;
            };

            Function<Function, Void> transformerRunner = v -> {
                mainGroup.parallelStream().map(cpc -> new Pair<>(cpc, cpcToRawTitleMap.get(cpc.getName()))).forEach(e -> {
                    v.apply(e);
                });
                return null;
            };

            Function<Function<Map<String, Object>, Void>, Void> function = attrFunction -> {
                stage1.runSamplingIterator(transformer, transformerRunner, attrFunction);
                return null;
            };

            stage1.buildVocabularyCounts(function, attributes -> {
                CPC cpc = (CPC) attributes.get("CPC");
                cpc.setKeywords(new HashSet<>((Collection<MultiStem>) attributes.get(Stage.APPEARED)));
                return null;
            });

            Map<MultiStem, AtomicLong> wordToDocCounter = stage1.get();
            Map<MultiStem, MultiStem> selfMap = stage1.get().keySet().parallelStream().collect(Collectors.toMap(e -> e, e -> e));
            System.out.println("Vocab size: " + wordToDocCounter.size());

            AtomicInteger cnt = new AtomicInteger(0);
            cpcHierarchy.getLabelToCPCMap().values().parallelStream().filter(cpc -> mainGroup.contains(cpc)).forEach(cpc -> {
                if (cpc.getKeywords() == null || cpc.getKeywords().isEmpty()) {
                    return;
                }
                // pick the one with best tfidf
                cpc.getKeywords().forEach(word -> {
                    double docCount = wordToDocCounter.getOrDefault(word, new AtomicLong(1)).get();
                    double tf = 1d;
                    double idf = Math.log(cpcHierarchy.getLabelToCPCMap().size() / (docCount));
                    double u = word.getStems().length;
                    double l = word.toString().length();
                    double score = tf * idf * u * u * l;
                    if (word.getStems().length > 1) {
                        double denom = Stream.of(word.getStems()).mapToDouble(stem -> wordToDocCounter.getOrDefault(new MultiStem(new String[]{stem}, -1), new AtomicLong(1)).get()).average().getAsDouble();
                        score *= docCount / Math.sqrt(denom);
                    }
                    word.setScore((float) score);
                });
                Set<MultiStem> temp = Collections.synchronizedSet(new HashSet<>());
                temp.addAll(cpc.getKeywords().stream().sorted((s1, s2) -> Float.compare(s2.getScore(), s1.getScore())).map(word -> selfMap.get(word)).limit(5).collect(Collectors.toList()));
                cpc.setKeywords(temp);
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("" + cnt.get() + " / " + cpcHierarchy.getLabelToCPCMap().size());
                }
            });

            System.out.println("Main group: " + mainGroup.size());
            Set<MultiStem> stems = mainGroup.parallelStream().filter(cpc -> cpc.getKeywords() != null).flatMap(cpc -> cpc.getKeywords().stream()).collect(Collectors.toSet());
            System.out.println("num stems: " + stems.size());
            System.out.println("total words: " + stage1.get().size());
        }
        Set<MultiStem> technologyStems = cpcHierarchy.getLabelToCPCMap().values().parallelStream().filter(cpc -> cpc.getKeywords() != null).flatMap(cpc -> cpc.getKeywords().stream()).collect(Collectors.toSet());
        Map<MultiStem,Set<CPC>> multiStemToCPCMap = Collections.synchronizedMap(new HashMap<>());
        cpcHierarchy.getLabelToCPCMap().values().parallelStream().filter(cpc->cpc.getKeywords()!=null).forEach(cpc->{
            cpc.getKeywords().forEach(word->{
                synchronized (multiStemToCPCMap) {
                    multiStemToCPCMap.putIfAbsent(word,new HashSet<>());
                    multiStemToCPCMap.get(word).add(cpc);
                }
            });
        });
        System.out.println("total num stems: " + technologyStems.size());
        //Stage5 stage5 = new Stage5(stage1, stems, new TimeDensityModel());
        //stage5.run(true);
        data = multiStemToCPCMap;

        return data;
    }

}
