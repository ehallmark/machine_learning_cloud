package models.keyphrase_prediction.stages;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import elasticsearch.DataIngester;
import lombok.Getter;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.TechnologyScorer;

import org.apache.commons.math3.linear.RealMatrix;
import org.elasticsearch.search.SearchHit;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class CPCDensityStage extends Stage<Set<MultiStem>> {
    private double minValue;
    @Getter
    private CPCHierarchy hierarchy = new CPCHierarchy();
    public CPCDensityStage(Set<MultiStem> keywords, Model model, CPCHierarchy hierarchy) {
        super(model);
        this.hierarchy=hierarchy;
        this.data = keywords;
        this.minValue = Double.MIN_VALUE;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            if(hierarchy.getTopLevel()==null) {
                hierarchy.loadGraph();
            }
            // apply filter 3
            System.out.println("Num keywords before stage 4: " + data.size());
            RealMatrix T = buildTMatrix()._2;

            System.out.println("Applying filters...");
            data = applyFilters(new TechnologyScorer(), T, data, defaultLower, defaultUpper, minValue);
            System.out.println("Num keywords after stage 4: " + data.size());

            Database.saveObject(data, getFile());
            // write to csv for records
            KeywordModelRunner.writeToCSV(data, new File(getFile().getAbsoluteFile() + ".csv"));
        } else {
            try {
                loadData();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    public Pair<Map<String,Integer>,RealMatrix> buildTMatrix() {
        return buildTMatrix(true);
    }

    public Pair<Map<String,Integer>,RealMatrix> buildTMatrix(boolean reindex) {
        if(reindex) KeywordModelRunner.reindex(data);
        Map<MultiStem,Integer> multiStemIdxMap = data.parallelStream().collect(Collectors.toMap(e->e,e->e.getIndex()));

        // create cpc code co-occurrrence statistics
        List<String> allCpcCodes = Database.getClassCodeToClassTitleMap().keySet().parallelStream().map(cpc-> ClassCodeHandler.convertToLabelFormat(cpc)).distinct()
                .filter(label->{
                    CPC cpc = hierarchy.getLabelToCPCMap().get(label);
                    return cpc!=null&&cpc.getNumParts()<5;
                }).collect(Collectors.toList());
        System.out.println("Num cpc codes found: "+allCpcCodes.size());
        Map<String,Integer> cpcCodeIndexMap = Collections.synchronizedMap(new HashMap<>());

        {
            AtomicInteger idx = new AtomicInteger(0);
            allCpcCodes.forEach(cpc -> cpcCodeIndexMap.put(cpc, idx.getAndIncrement()));
        }

        RealMatrix matrix = new OpenMapBigRealMatrix(data.size(),allCpcCodes.size());

        AtomicInteger cpcCount = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);

        Map<String,Set<String>> filingToCPCMap = Collections.synchronizedMap(new HashMap<>());
        {
            AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
            AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
            assetToFilingMap.getPatentDataMap().entrySet().parallelStream().forEach(e -> {
                Set<String> cpcs = assetToCPCMap.getPatentDataMap().get(e.getKey());
                if (cpcs != null) {
                    filingToCPCMap.putIfAbsent(e.getValue(), cpcs);
                }
            });
            assetToFilingMap.getApplicationDataMap().entrySet().parallelStream().forEach(e -> {
                Set<String> cpcs = assetToCPCMap.getApplicationDataMap().get(e.getKey());
                if (cpcs != null) {
                    filingToCPCMap.putIfAbsent(e.getValue(), cpcs);
                }
            });

            System.out.println("Num filings with cpcs: " + filingToCPCMap.size());
        }

        Function<org.nd4j.linalg.primitives.Pair<String,Map<MultiStem,Integer>>,Void> attributesFunction = pair-> {
            String asset = pair.getFirst();
            Map<MultiStem,Integer> wordCounts = pair.getSecond();
            total.getAndIncrement();

            // filings
            Map<CPC,Double> cpcToScoreMap = computeCPCToScoreMap(asset,filingToCPCMap, hierarchy);
            if(cpcToScoreMap.isEmpty()) {
                return null;
            }
            Collection<MultiStem> multiStems = wordCounts.keySet();
            int[] multiStemIndices = multiStems.stream().map(m->multiStemIdxMap.get(m)).filter(i->i!=null).mapToInt(i->i).toArray();
            if(multiStemIndices.length>0) {
                Map<Integer,Double> cpcIndicesToScores = cpcToScoreMap.entrySet().stream().filter(e->cpcCodeIndexMap.containsKey(e.getKey().getName()))
                        .collect(Collectors.toMap(e->cpcCodeIndexMap.get(e.getKey().getName()),e->e.getValue()/e.getKey().numSubclasses()));
                if (cpcIndicesToScores.size()>0) {
                    cpcIndicesToScores.entrySet().forEach(e->{
                        for (int stemIdx : multiStemIndices) {
                            matrix.addToEntry(stemIdx, e.getKey(), e.getValue());
                        }
                    });
                    cpcCount.getAndIncrement();
                }
            }
            return null;
        };

        runSamplingIteratorWithLabels(attributesFunction,100000);
        System.out.println("Found "+cpcCount.get()+" / "+total.get()+" CPCS.");

        return new Pair<>(cpcCodeIndexMap,matrix);
    }

    public static Map<CPC,Double> computeCPCToScoreMap(String asset, Map<String,Set<String>> filingCPCMap, CPCHierarchy hierarchy) {
        Collection<String> currentCpcs = filingCPCMap.getOrDefault(asset,Collections.emptySet());
        // add potential cpcs from keywords
        currentCpcs = currentCpcs.stream().map(cpc->ClassCodeHandler.convertToLabelFormat(cpc)).collect(Collectors.toList());
        Map<CPC,Double> cpcToScoreMap = new HashMap<>();

        Collection<CPC> cpcs = currentCpcs.stream().map(cpc->hierarchy.getLabelToCPCMap().get(cpc)).filter(cpc->cpc!=null).flatMap(cpc->hierarchy.cpcWithAncestors(cpc.getName()).stream()).collect(Collectors.toList());
        for(CPC cpc : cpcs) {
            if(cpcToScoreMap.containsKey(cpc)) {
                cpcToScoreMap.put(cpc,cpcToScoreMap.get(cpc)+(Math.exp(cpc.getNumParts())));
            } else {
                cpcToScoreMap.put(cpc,(Math.exp(cpc.getNumParts())));
            }
        }
        return cpcToScoreMap;
    }

}