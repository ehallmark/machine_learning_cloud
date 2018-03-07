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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/21/2017.
 */
public class WordOrderStage extends Stage<Set<MultiStem>>  {
    private Map<MultiStem,AtomicLong> docMap;
    public WordOrderStage(Collection<MultiStem> multiStems, Map<MultiStem,AtomicLong> docMap, Model model) {
        super(model);
        this.docMap=docMap;
        this.data = multiStems==null? Collections.emptySet() : new HashSet<>(multiStems);
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before word order stage: " + data.size());
            Map<MultiStem,Long> dataCountMap = Collections.synchronizedMap(
                    data.parallelStream().collect(Collectors.toMap(d->d,d->docMap.get(d).get()))
            );
            Map<String,Long> dataCountMapFromPhrase = Collections.synchronizedMap(new HashMap<>());
            dataCountMap.forEach((k,v)->{
                dataCountMapFromPhrase.merge(k.getBestPhrase(),v,(v1,v2)->v1>v2?v1:v2);
            });
            data.parallelStream().forEach(d->{
                String[] phrase = d.getBestPhrase().split(" ");
                String[] stems = d.getStems();
                if(stems.length==1) return;
                MultiStem[] stemsToCheck;
                String[] phrasesToCheck;
                if(stems.length==2) {
                    stemsToCheck = new MultiStem[]{new MultiStem(new String[]{stems[1],stems[0]},-1)};
                    phrasesToCheck = new String[]{String.join(" ",phrase[1],phrase[0])};
                } else if(stems.length==3) {
                    stemsToCheck = new MultiStem[]{
                            new MultiStem(new String[]{stems[0],stems[2],stems[1]},-1),
                            new MultiStem(new String[]{stems[1],stems[0],stems[2]},-1),
                            new MultiStem(new String[]{stems[1],stems[2],stems[0]},-1),
                            new MultiStem(new String[]{stems[2],stems[0],stems[1]},-1),
                            new MultiStem(new String[]{stems[2],stems[1],stems[0]},-1),
                    };
                    phrasesToCheck = new String[]{
                            String.join(" ",phrase[0],phrase[2],phrase[1]),
                            String.join(" ",phrase[1],phrase[0],phrase[2]),
                            String.join(" ",phrase[1],phrase[2],phrase[0]),
                            String.join(" ",phrase[2],phrase[0],phrase[1]),
                            String.join(" ",phrase[2],phrase[1],phrase[0])
                    };

                } else {
                    return;
                }
                final long numToBeat = dataCountMap.get(d);
                for(MultiStem toCheck : stemsToCheck) {
                    if(toCheck!=null) {
                        synchronized (dataCountMap) {
                            Long num = dataCountMap.get(toCheck);
                            if(num!=null&&num>numToBeat) {
                                System.out.println("Removing "+d.getBestPhrase()+" for "+toCheck.toString());
                                dataCountMap.remove(d);
                                return;
                            }
                        }
                    }
                }
                for(String toCheck : phrasesToCheck) {
                    if(toCheck!=null) {
                        synchronized (dataCountMapFromPhrase) {
                            Long num = dataCountMapFromPhrase.get(toCheck);
                            if(num!=null&&num>numToBeat) {
                                System.out.println("Removing "+d.getBestPhrase()+" for "+toCheck);
                                dataCountMapFromPhrase.remove(d);
                                return;
                            }
                        }
                    }
                }
            });
            data = Collections.synchronizedSet(new HashSet<>(dataCountMap.keySet()));
            System.out.println("Num keywords after word order stage: " + data.size());

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
