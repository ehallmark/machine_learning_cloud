package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.apache.commons.math3.linear.MatrixUtils;

import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage2 extends Stage<Set<MultiStem>> {
    private long targetCardinality;
    private Map<MultiStem,AtomicLong> documentsAppearedInCounter;
    public Stage2(Map<MultiStem,AtomicLong> documentsAppearedInCounter, Model model) {
        super(model);
        this.documentsAppearedInCounter=documentsAppearedInCounter;
        this.targetCardinality=model.getKw()*model.getK1();
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // filter outliers
            System.out.println("Num keywords before stage 2: " + documentsAppearedInCounter.size());
            KeywordModelRunner.reindex(documentsAppearedInCounter.keySet());

            // compute scores
            data = new ArrayList<>(documentsAppearedInCounter.entrySet()).parallelStream().map(e->{
                MultiStem multiStem = e.getKey();
                double score = e.getValue().doubleValue();
                if(multiStem.getStems().length > 1) {
                    double denom = Math.pow(Stream.of(multiStem.getStems()).map(s -> documentsAppearedInCounter.getOrDefault(new MultiStem(new String[]{s}, -1), new AtomicLong(1))).mapToDouble(d -> d.doubleValue()).reduce((d1, d2) -> d1 * d2).getAsDouble(), 1d / multiStem.getStems().length);
                    score = (score * e.getValue().doubleValue() * multiStem.getStems().length) / denom;
                }
                multiStem.setScore((float)score);
                return multiStem;
            }).sorted((s1,s2)->Float.compare(s2.getScore(),s1.getScore())).limit(targetCardinality).collect(Collectors.toSet());
            System.out.println("Num keywords after stage 2: " + data.size());

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

}
