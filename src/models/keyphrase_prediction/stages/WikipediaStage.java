package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import seeding.Database;
import wiki.ScrapeWikipedia;

import java.io.File;
import java.util.Set;

/**
 * Created by ehallmark on 9/12/17.
 */
public class WikipediaStage extends Stage<Set<MultiStem>> {
    public WikipediaStage(Set<MultiStem> multiStems, Model model) {
        super(model);
        this.data=multiStems;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // filter outliers
            System.out.println("Num keywords before wikipedia stage: " + data.size());
            data = ScrapeWikipedia.filterMultistems(data);
            System.out.println("Num keywords after wikipedia stage: " + data.size());

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
