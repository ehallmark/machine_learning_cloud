package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.RealMatrix;
import seeding.Database;
import tools.OpenMapBigRealMatrix;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage3 extends Stage<Set<MultiStem>> {
    private static final boolean debug = false;
    private Map<MultiStem,MultiStem> multiStemToSelfMap;
    private double minValue;
    public Stage3(Collection<MultiStem> multiStems, Model model) {
        super(model);
        this.data = new HashSet<>(multiStems==null? Collections.emptySet():multiStems);
        this.multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
        this.minValue = model.getDefaultMinValue();
    }



    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before stage 3: " + data.size());
            RealMatrix M = buildMMatrix(data,multiStemToSelfMap);
            System.out.println("M Matrix dimensions: ("+M.getRowDimension()+"x"+M.getColumnDimension()+")");
            data = applyFilters(new TermhoodScorer(), M, data, defaultLower, defaultUpper, minValue);
            System.out.println("Num keywords after stage 3: " + data.size());

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

    public RealMatrix buildMMatrix(Collection<MultiStem> data, Map<MultiStem,MultiStem> multiStemToSelfMap) {
        return this.buildMMatrix(data,multiStemToSelfMap,attrFunc->{
            runSamplingIterator(attrFunc, 100000);
            return null;
        });
    }

    public RealMatrix buildMMatrix(Collection<MultiStem> data, Map<MultiStem,MultiStem> multiStemToSelfMap, Function<Function<Map<MultiStem,Integer>,Void>,Void> function) {
        RealMatrix matrix = new OpenMapBigRealMatrix(data.size(),data.size());
        KeywordModelRunner.reindex(data);

        Function<Map<MultiStem,Integer>,Void> attributesFunction = appeared -> {
            if(appeared==null) return null;

            Collection<MultiStem> cooccurringStems = appeared.keySet().stream().filter(docStem->data.contains(docStem)).map(docStem->multiStemToSelfMap.get(docStem)).collect(Collectors.toList());

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            // Unavoidable n-squared part
            for(MultiStem stem1 : cooccurringStems) {
                for (MultiStem stem2 : cooccurringStems) {
                    matrix.addToEntry(stem1.getIndex(), stem2.getIndex(), 1);
                }
            }
            return null;
        };

        function.apply(attributesFunction);
        return matrix;
    }


}
