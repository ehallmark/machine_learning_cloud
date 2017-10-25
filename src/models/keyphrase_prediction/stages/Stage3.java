package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import model.edges.Edge;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.SparseRealMatrix;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import spire.math.algebraic.Mul;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage3 extends Stage<Set<MultiStem>> {
    private static final boolean debug = false;
    private Map<MultiStem,MultiStem> multiStemToSelfMap;
    private double lowerBound;
    private double upperBound;
    private double minValue;
    public Stage3(Collection<MultiStem> multiStems, Model model, int year) {
        super(model, year);
        this.data = new HashSet<>(multiStems);
        this.multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
        this.lowerBound=model.getStage3Lower();
        this.upperBound=model.getStage3Upper();
        this.minValue = model.getStage3Min();
    }



    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before stage 3: " + data.size());
            SparseRealMatrix M = buildMMatrix(data,multiStemToSelfMap);
            data = applyFilters(new TermhoodScorer(), M, data, lowerBound, upperBound, minValue);
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

    public SparseRealMatrix buildMMatrix(Collection<MultiStem> data, Map<MultiStem,MultiStem> multiStemToSelfMap) {
        return this.buildMMatrix(data,multiStemToSelfMap,attrFunc->{
            runSamplingIterator(attrFunc);
            return null;
        });
    }

    public SparseRealMatrix buildMMatrix(Collection<MultiStem> data, Map<MultiStem,MultiStem> multiStemToSelfMap, Function<Function<Map<String,Object>,Void>,Void> function) {
        SparseRealMatrix matrix = new OpenMapBigRealMatrix(data.size(),data.size());
        KeywordModelRunner.reindex(data);

        Function<Map<String,Object>,Void> attributesFunction = attributes -> {
            Collection<MultiStem> appeared = (Collection<MultiStem>)attributes.get(APPEARED);
            Collection<MultiStem> cooccurringStems = appeared.stream().filter(docStem->data.contains(docStem)).map(docStem->multiStemToSelfMap.get(docStem)).collect(Collectors.toList());

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
