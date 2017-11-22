package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/21/2017.
 */
public class ValidWordStage extends Stage<Set<MultiStem>>  {
    private static final File dictionaryFile = new File(Constants.DATA_FOLDER+"word_list.txt");
    public ValidWordStage(Collection<MultiStem> multiStems, Model model) {
        super(model);
        this.data = multiStems==null? Collections.emptySet() : new HashSet<>(multiStems);
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before valid word stage: " + data.size());
            System.out.println("Reading dictionary...");
            Set<String> dictionary = Collections.synchronizedSet(new HashSet<>());
            try {
                LineIterator iter = FileUtils.lineIterator(dictionaryFile);
                while (iter.hasNext()) {
                    dictionary.add(iter.next().toLowerCase().trim());
                }
                LineIterator.closeQuietly(iter);
                System.out.println("Finished reading dictionary. Size: " + dictionary.size());
            } catch(Exception e) {
                e.printStackTrace();
            }

            data = data.parallelStream().filter(stem->{
                String bestPhrase = stem.getBestPhrase();
                String[] words = bestPhrase.split("\\s+");
                boolean valid = true;
                for(String word : words) {
                    if(!dictionary.contains(word)) {
                        valid = false;
                        break;
                    }
                }
                return valid;
            }).collect(Collectors.toSet());
            System.out.println("Num keywords after valid word stage: " + data.size());

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
}
