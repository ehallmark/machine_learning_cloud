package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage2 implements Stage<Collection<MultiStem>> {
    private static final File stage2File = new File("data/keyword_model_keywords_set_stage2.jobj");
    private Collection<MultiStem> keywords;
    private long targetCardinality;
    private int year;
    private Map<MultiStem,AtomicLong> keywordsCounts;
    private double upperBound;
    private double lowerBound;
    private String name;
    public Stage2(Map<MultiStem,AtomicLong> keywordsCounts, Model model, int year) {
        this.keywordsCounts=keywordsCounts;
        this.year = year;
        this.targetCardinality=model.getKw()*model.getK1();
        this.upperBound=model.getStage2Upper();
        this.lowerBound=model.getStage2Lower();
        this.name=model.getModelName();
    }

    @Override
    public Collection<MultiStem> get() {
        return keywords;
    }

    @Override
    public void loadData() {
        keywords = (Collection<MultiStem>)Database.loadObject(getFile(year));
    }

    @Override
    public File getFile(int year) {
        return new File(stage2File.getAbsolutePath()+name+year);
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        if(run) {
            // filter outliers
            keywords = new HashSet<>(keywordsCounts.keySet());

            KeywordModelRunner.reindex(keywords);

            // apply filter 1
            double[] F = buildFMatrix(keywordsCounts);
            keywords = KeywordModelRunner.applyFilters(new UnithoodScorer(), MatrixUtils.createRealMatrix(new double[][]{F}), keywords, targetCardinality, lowerBound,upperBound);
            Database.saveObject(keywords, getFile(year));
            // write to csv for records
            KeywordModelRunner.writeToCSV(keywords,new File("data/keyword_model_stage2-"+year+name+".csv"));
        } else {
            loadData();
        }
        return keywords;
    }

    private static double[] buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        double[] array = new double[multiStemMap.size()];
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array[e.getKey().getIndex()]=e.getValue().get();
        });
        return array;
    }


}
