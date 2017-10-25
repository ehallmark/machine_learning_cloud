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
import org.apache.commons.math3.linear.OpenMapRealMatrix;
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
    @Getter
    private static CPCHierarchy hierarchy = new CPCHierarchy();
    static {
        hierarchy.loadGraph();
    }
    public CPCDensityStage(Set<MultiStem> keywords, Model model, int year) {
        super(model,year);
        this.data = keywords;
        this.minValue = model.getDefaultMinValue();
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 3
            RealMatrix T = buildTMatrix()._2;
            // save t matrix
            data = applyFilters(new TechnologyScorer(), T, data, defaultLower, defaultUpper, minValue);
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
        List<String> allCpcCodes = Database.getClassCodeToClassTitleMap().keySet().parallelStream().map(cpc-> ClassCodeHandler.convertToLabelFormat(cpc)).distinct().collect(Collectors.toList());
        System.out.println("Num cpc codes found: "+allCpcCodes.size());
        Map<String,Integer> cpcCodeIndexMap = Collections.synchronizedMap(new HashMap<>());

        {
            AtomicInteger idx = new AtomicInteger(0);
            allCpcCodes.forEach(cpc -> cpcCodeIndexMap.put(cpc, idx.getAndIncrement()));
        }

        RealMatrix matrix = new OpenMapBigRealMatrix(data.size(),allCpcCodes.size());

        final AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Function<Map<String,Object>,Void> attributesFunction = attributes-> {
            String asset = (String)attributes.get(ASSET_ID);

            Collection<String> currentCpcs = assetToCPCMap.getApplicationDataMap().getOrDefault(asset,assetToCPCMap.getPatentDataMap().get(asset));
            if(currentCpcs==null||currentCpcs.isEmpty()) return null;

            currentCpcs = currentCpcs.stream().map(cpc->ClassCodeHandler.convertToLabelFormat(cpc)).collect(Collectors.toList());
            Collection<CPC> cpcs = currentCpcs.stream().map(cpc->hierarchy.getLabelToCPCMap().get(cpc)).filter(cpc->cpc!=null).distinct().collect(Collectors.toList());
            Collection<MultiStem> multiStems = (Collection<MultiStem>) attributes.get(APPEARED);
            int[] multiStemIndices = multiStems.stream().map(m->multiStemIdxMap.get(m)).filter(i->i!=null).mapToInt(i->i).toArray();
            if(multiStemIndices.length>0) {
                while (cpcs.size() > 0) {
                    Map<Integer,Double> cpcIndicesToScores = cpcs.stream().filter(cpc->cpcCodeIndexMap.containsKey(cpc.getName()))
                            .collect(Collectors.toMap(cpc->cpcCodeIndexMap.get(cpc.getName()),cpc->1d/cpc.numSubclasses()));
                    if (cpcIndicesToScores.size()>0) {
                        for (int stemIdx : multiStemIndices) {
                            cpcIndicesToScores.entrySet().forEach(e->{
                                matrix.addToEntry(stemIdx, e.getKey(), e.getValue());
                            });
                        }
                    }
                    cpcs = cpcs.stream().map(cpc -> cpc.getParent()).filter(p -> p != null).distinct().collect(Collectors.toList());
                }
            }
            return null;
        };

        runSamplingIterator(attributesFunction);

        return new Pair<>(cpcCodeIndexMap,matrix);
    }

}
