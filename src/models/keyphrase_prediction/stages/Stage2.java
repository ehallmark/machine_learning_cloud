package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;

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
    private Map<MultiStem,AtomicLong> documentsAppearedInCounter;
    public Stage2(Map<MultiStem,AtomicLong> documentsAppearedInCounter, Model model) {
        super(model);
        this.documentsAppearedInCounter=documentsAppearedInCounter;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // filter outliers
            System.out.println("Num keywords before stage 2: " + documentsAppearedInCounter.size());
            KeywordModelRunner.reindex(documentsAppearedInCounter.keySet());
            int toSkip = (int) (1d-defaultUpper)*documentsAppearedInCounter.size();
            int toKeep = documentsAppearedInCounter.size()-toSkip-(int)(documentsAppearedInCounter.size()*defaultLower);
            // compute scores
            data = new ArrayList<>(documentsAppearedInCounter.entrySet()).parallelStream().map(e->{
                MultiStem multiStem = e.getKey();
                double score = e.getValue().doubleValue()*Math.exp(multiStem.getStems().length)*Math.log(multiStem.toString().length());
                multiStem.setScore((float)score);
                return multiStem;
            }).sorted((s1,s2)->Float.compare(s2.getScore(),s1.getScore())).skip(toSkip).limit(toKeep).collect(Collectors.toSet());
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
