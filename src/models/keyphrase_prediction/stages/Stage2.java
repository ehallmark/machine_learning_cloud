package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.scorers.UnithoodScorer;
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
    private Stage1 stage1;
    private Collection<MultiStem> keywords;
    private long targetCardinality;
    public Stage2(Stage1 stage1, long targetCardinality) {
        this.stage1=stage1;
        this.targetCardinality=targetCardinality;
    }

    @Override
    public Collection<MultiStem> get() {
        return keywords;
    }

    @Override
    public void loadData() {
        keywords = (Collection<MultiStem>)Database.loadObject(getFile(-1));
    }

    @Override
    public File getFile(int year) {
        return new File(stage2File.getAbsolutePath());
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        final int minTokenFrequency = 30;
        final int maxTokenFrequency = 100000;
        if(run) {
            // filter outliers
            Map<MultiStem,AtomicLong> keywordsCounts = stage1.get();
            keywordsCounts = truncateBetweenLengths(keywordsCounts, minTokenFrequency, maxTokenFrequency);
            keywords = new HashSet<>(keywordsCounts.keySet());

            KeywordModelRunner.reindex(keywords);

            // apply filter 1
            INDArray F = buildFMatrix(keywordsCounts);
            keywords = KeywordModelRunner.applyFilters(new UnithoodScorer(), F, keywords, targetCardinality, 0, Double.MAX_VALUE);
            Database.saveObject(keywords, stage2File);
            // write to csv for records
            KeywordModelRunner.writeToCSV(keywords,new File("data/keyword_model_stage2.csv"));
        } else {
            loadData();
        }
        return keywords;
    }

    private static Map<MultiStem,AtomicLong> truncateBetweenLengths(Map<MultiStem,AtomicLong> stemMap, int min, int max) {
        return stemMap.entrySet().parallelStream().filter(e->e.getValue().get()>=min&&e.getValue().get()<=max).collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
    }

    private static INDArray buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        INDArray array = Nd4j.create(multiStemMap.size());
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array.putScalar(e.getKey().getIndex(),e.getValue().get());
        });
        return array;
    }


}
