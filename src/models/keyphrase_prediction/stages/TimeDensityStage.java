package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import org.apache.commons.math3.linear.RealMatrix;
import seeding.Database;
import tools.OpenMapBigRealMatrix;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import util.Pair;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/12/17.
 */
public class TimeDensityStage extends Stage<Set<MultiStem>> {
    private static final boolean debug = false;
    private double minValue = 0d;
    public TimeDensityStage(Set<MultiStem> keywords, Model model, int year) {
        super(model, year);
        this.data = keywords;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 3
            RealMatrix T = buildTMatrix()._2;

            System.out.println("Num keywords before time density: " + data.size());
            data = applyFilters(new TechnologyScorer(), T, data, defaultLower, defaultUpper, minValue);
            data = data.parallelStream().filter(d->d.getScore()>0f).collect(Collectors.toSet());
            System.out.println("Num keywords after time density: " + data.size());

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


    public Pair<Map<Integer,Integer>,RealMatrix> buildTMatrix() {
        KeywordModelRunner.reindex(data);
        Map<MultiStem,Integer> multiStemIdxMap = data.parallelStream().collect(Collectors.toMap(e->e,e->e.getIndex()));
        Map<Integer,Integer> dateToIndexMap = Collections.synchronizedMap(new HashMap<>());
        {
            LocalDate date = LocalDate.of(year,1,1).minusYears(2);
            int i = 0;
            while (date.isBefore(LocalDate.of(year+3,1,1))) {
                int time = date.getYear() * 12 + date.getMonthValue();
                dateToIndexMap.put(time, i);
                date = date.plusMonths(1);
                i++;
            }
        }
        RealMatrix matrix = new OpenMapBigRealMatrix(data.size(),dateToIndexMap.size());

        Function<Map<String,Object>,Void> attributesFunction = attributes-> {
            LocalDate date = (LocalDate)attributes.get(DATE);
            int time = date.getYear()*12 + date.getMonthValue();
            Integer dateIdx = dateToIndexMap.get(time);
            if(dateIdx==null) return null;

            Collection<MultiStem> cooccurringStems = Collections.synchronizedCollection(new ArrayList<>());

            Collection<MultiStem> multiStems = (Collection<MultiStem>)attributes.get(APPEARED);
            multiStems.parallelStream().forEach(stem->{
                Integer idx = multiStemIdxMap.get(stem);
                if(idx!=null) {
                    cooccurringStems.add(stem);
                }
            });

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            for(MultiStem stem : cooccurringStems) {
                matrix.addToEntry(multiStemIdxMap.get(stem), dateIdx, 1);
            }

            return null;
        };

        runSamplingIterator(attributesFunction);

        return new Pair<>(dateToIndexMap,matrix);
    }

}
