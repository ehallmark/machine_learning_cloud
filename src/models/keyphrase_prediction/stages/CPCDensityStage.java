package models.keyphrase_prediction.stages;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import elasticsearch.DataIngester;
import lombok.Getter;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.elasticsearch.search.SearchHit;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
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
    double cpcRatio = 0d;
    @Getter
    private CPCHierarchy hierarchy = new CPCHierarchy();
    private Map<MultiStem,Set<CPC>> multiStemCPCMap;
    public CPCDensityStage(Set<MultiStem> keywords, Model model, int year, Map<MultiStem,Set<CPC>> multiStemCPCMap, CPCHierarchy hierarchy) {
        super(model,year);
        this.hierarchy=hierarchy;
        this.multiStemCPCMap=multiStemCPCMap;
        this.data = keywords;
        this.minValue = Double.MIN_VALUE;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 3
            RealMatrix T = buildTMatrix()._2;
            double threshold = 1d-(1d-defaultUpper)-defaultLower;
            if(cpcRatio >= threshold) {
                System.out.println("Applying filters...");
                data = applyFilters(new TechnologyScorer(), T, data, defaultLower, defaultUpper, minValue);
            } else {
                System.out.println("Not enough cpc codes to apply filters...");
                System.out.println(""+threshold+ " > "+cpcRatio);
            }
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
        final AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Function<Map<String,Object>,Void> attributesFunction = attributes-> {
            String asset = (String)attributes.get(ASSET_ID);
            Map<MultiStem,AtomicInteger> wordCounts = (Map<MultiStem,AtomicInteger>)attributes.get(APPEARED_WITH_COUNTS);
            total.getAndIncrement();

            Map<CPC,Double> cpcToScoreMap = computeCPCToScoreMap(asset,assetToCPCMap.getPatentDataMap(),assetToCPCMap.getApplicationDataMap(),
                    hierarchy, wordCounts, multiStemCPCMap);
            if(cpcToScoreMap.isEmpty()) {
                return null;
            }
            Collection<MultiStem> multiStems = (Collection<MultiStem>) attributes.get(APPEARED);
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

        runSamplingIterator(attributesFunction);
        System.out.println("Found "+cpcCount.get()+" / "+total.get()+" CPCS.");
        cpcRatio = new Double(cpcCount.get())/total.get();

        return new Pair<>(cpcCodeIndexMap,matrix);
    }

    public static Map<CPC,Double> computeCPCToScoreMap(String asset, Map<String,Set<String>> patentCPCMap, Map<String,Set<String>> appCPCMap, CPCHierarchy hierarchy, Map<MultiStem,AtomicInteger> wordCounts, Map<MultiStem,Set<CPC>> multiStemCPCMap) {
        Collection<String> currentCpcs = patentCPCMap.getOrDefault(asset,appCPCMap.getOrDefault(asset,Collections.emptySet()));
        // add potential cpcs from keywords
        currentCpcs = currentCpcs.stream().map(cpc->ClassCodeHandler.convertToLabelFormat(cpc)).collect(Collectors.toList());
        Map<CPC,Double> cpcToScoreMap = new HashMap<>(wordCounts.entrySet().stream().flatMap(e->{
            Set<CPC> found = multiStemCPCMap.getOrDefault(e.getKey(),Collections.emptySet());
            double count = e.getValue().get();
            return found.stream().map(cpc->new Pair<>(cpc,(count/(found.size()*cpc.getKeywords().size()))));
        }).flatMap(p->hierarchy.cpcWithAncestors(p._1.getName()).stream().map(a->new Pair<>(a,p._2/a.numSubclasses()))).collect(Collectors.groupingBy(pair->pair._1,Collectors.summingDouble(p->p._2))));
        Collection<CPC> cpcs = currentCpcs.stream().map(cpc->hierarchy.getLabelToCPCMap().get(cpc)).filter(cpc->cpc!=null).flatMap(cpc->hierarchy.cpcWithAncestors(cpc.getName()).stream()).collect(Collectors.toList());
        for(CPC cpc : cpcs) {
            if(cpcToScoreMap.containsKey(cpc)) {
                cpcToScoreMap.put(cpc,cpcToScoreMap.get(cpc)+(1d/cpc.numSubclasses()));
            } else {
                cpcToScoreMap.put(cpc,1d/cpc.numSubclasses());
            }
        }
        return cpcToScoreMap;
    }

}
