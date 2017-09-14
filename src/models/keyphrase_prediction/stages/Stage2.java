package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
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
    public Stage2(Map<MultiStem,AtomicLong> keywordsCounts, int year, long targetCardinality) {
        this.keywordsCounts=keywordsCounts;
        this.year = year;
        this.targetCardinality=targetCardinality;
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
        return new File(stage2File.getAbsolutePath()+year);
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        if(run) {
            // filter outliers
            keywords = new HashSet<>(keywordsCounts.keySet());

            KeywordModelRunner.reindex(keywords);

            // apply filter 1
            INDArray F = buildFMatrix(keywordsCounts);
            keywords = KeywordModelRunner.applyFilters(new UnithoodScorer(), MatrixUtils.createRealMatrix(new double[][]{F.data().asDouble()}), keywords, targetCardinality, 0.0, 0.7);
            Database.saveObject(keywords, getFile(year));
            // write to csv for records
            KeywordModelRunner.writeToCSV(keywords,new File("data/keyword_model_stage2-"+year+".csv"));
        } else {
            loadData();
        }
        return keywords;
    }

    private static INDArray buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        INDArray array = Nd4j.create(multiStemMap.size());
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array.putScalar(e.getKey().getIndex(),e.getValue().get());
        });
        return array;
    }


}
