package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.KeywordScorer;
import org.apache.commons.math3.linear.RealMatrix;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/12/17.
 */
public abstract class Stage<V> {
    private static final boolean debug = false;
    private static final File baseDir = new File(Constants.DATA_FOLDER+"technologyPredictionStages/");
    protected File mainDir;
    protected int year;
    protected V data;
    protected int sampling;
    public Stage(Model model, int year) {
        this.year=year;
        this.sampling=model.getSampling();
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException(baseDir.getAbsolutePath()+" must be a directory.");
        this.mainDir = new File(baseDir, model.getModelName());
        if(!mainDir.exists()) mainDir.mkdir();
        if(!mainDir.isDirectory()) throw new RuntimeException(mainDir.getAbsolutePath()+" must be a directory.");
    }
    protected void loadData() {
        data = (V) Database.tryLoadObject(getFile());
    }
    public V get() {
        return data;
    }
    public void set(V data) {
        this.data=data;
    }
    public File getFile() {
        return new File(mainDir,this.getClass().getSimpleName()+year);
    }

    public abstract V run(boolean run);

    public static Collection<MultiStem> applyFilters(KeywordScorer scorer, RealMatrix matrix, Collection<MultiStem> keywords, long maxNumToKeep, double lowerBoundPercent, double upperBoundPercent, double minValue) {
        Map<MultiStem,Double> scoreMap = scorer.scoreKeywords(keywords,matrix);
        long count = scoreMap.size();
        double skipFirst = lowerBoundPercent*count;
        double skipLast = (1.0-upperBoundPercent)*count;
        return scoreMap.entrySet().stream()
                .filter(e->e.getValue()>minValue)
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .skip((long)skipLast)
                .limit(Math.min(maxNumToKeep,count-(long)(skipFirst+skipLast)))
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    e.getKey().setScore(e.getValue().floatValue());
                    return e.getKey();
                })
                .collect(Collectors.toList());
    }
}
