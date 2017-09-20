package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.apache.commons.math3.linear.MatrixUtils;

import seeding.Database;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage2 extends Stage<Collection<MultiStem>> {
    private long targetCardinality;
    private Map<MultiStem,AtomicLong> keywordsCounts;
    private double upperBound;
    private double lowerBound;
    public Stage2(Map<MultiStem,AtomicLong> keywordsCounts, Model model, int year) {
        super(model, year);
        this.keywordsCounts=keywordsCounts;
        this.targetCardinality=model.getKw()*model.getK1();
        this.upperBound=model.getStage2Upper();
        this.lowerBound=model.getStage2Lower();
    }

    @Override
    public Collection<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // filter outliers
            data = new HashSet<>(keywordsCounts.keySet());

            KeywordModelRunner.reindex(data);

            // apply filter 1
            double[] F = buildFMatrix(keywordsCounts);
            data = KeywordModelRunner.applyFilters(new UnithoodScorer(), MatrixUtils.createRealMatrix(new double[][]{F}), data, targetCardinality, lowerBound,upperBound);
            Database.saveObject(data, getFile());
            // write to csv for records
            KeywordModelRunner.writeToCSV(data,new File(getFile().getAbsoluteFile()+".csv"));
        } else {
            try {
                loadData();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private static double[] buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        double[] array = new double[multiStemMap.size()];
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array[e.getKey().getIndex()]=e.getValue().get();
        });
        return array;
    }


}
