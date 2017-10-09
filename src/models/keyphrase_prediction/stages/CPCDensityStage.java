package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
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
    private static final boolean debug = false;
    private final int maxCpcLength;
    private double lowerBound;
    private double upperBound;
    private double minValue;
    public CPCDensityStage(Set<MultiStem> keywords, Model model) {
        super(model);
        this.data = keywords;
        this.maxCpcLength=model.getMaxCpcLength();
        this.upperBound=model.getStage4Upper();
        this.lowerBound=model.getStage4Lower();
        this.minValue = model.getStage4Min();
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 3
            RealMatrix T = buildTMatrix(maxCpcLength)._2;

            data = applyFilters(new TechnologyScorer(), T, data, lowerBound, upperBound, minValue);
            data = data.parallelStream().filter(d->d.getScore()>0f).collect(Collectors.toSet());
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


    public Pair<Map<String,Integer>,RealMatrix> buildTMatrix(int maxCpcLength) {
        KeywordModelRunner.reindex(data);
        Map<MultiStem,Integer> multiStemIdxMap = data.parallelStream().collect(Collectors.toMap(e->e,e->e.getIndex()));

        // create cpc code co-occurrrence statistics
        List<String> allCpcCodes = Database.getClassCodes().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
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

            int[] cpcIndices = currentCpcs.stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).map(cpc->cpcCodeIndexMap.get(cpc)).filter(cpc->cpc!=null).mapToInt(i->i).toArray();
            if(cpcIndices==null||cpcIndices.length==0) return null;

            Collection<MultiStem> cooccurringStems = Collections.synchronizedCollection(new ArrayList<>());

            Collection<MultiStem> multiStems = (Collection<MultiStem>)attributes.get(APPEARED);
            multiStems.parallelStream().forEach(stem->{
                Integer idx = multiStemIdxMap.get(stem);
                if(idx!=null) {
                    cooccurringStems.add(stem);
                }
            });

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            for(MultiStem stem : cooccurringStems) {
                int stemIdx = multiStemIdxMap.get(stem);
                for (int cpcIdx : cpcIndices) {
                    matrix.addToEntry(stemIdx, cpcIdx, 1);
                }
            }

            return null;
        };

        runSamplingIterator(attributesFunction);

        return new Pair<>(cpcCodeIndexMap,matrix);
    }

}
