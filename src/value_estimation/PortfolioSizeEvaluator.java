package value_estimation;

import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Database;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class PortfolioSizeEvaluator extends Evaluator {

    public PortfolioSizeEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Portfolio Size Value");
        setModel();
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        Collection<String> assignees = Database.getAssignees();
        Map<String,Double> map = new HashMap<>(assignees.size());
        assignees.forEach(assignee->{
            map.put(assignee,new Double(Database.getExactAssetCountFor(assignee)));
        });
        return map;
    }
}
