package value_estimation;

import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Database;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class AssigneeEvaluator extends Evaluator {
    static final File assigneeModelFile = new File("assignee_value_model.jobj");
    private static final File[] files = new File[]{
            CitationEvaluator.file,
            ClaimEvaluator.claimLengthModelFile,
            ClaimEvaluator.claimRatioModelFile,
            ClaimEvaluator.pendencyModelFile,
            MarketEvaluator.assetFamilyValueModelFile,
            MarketEvaluator.maintenanceFeeValueModelFile,
            MarketEvaluator.transactionValueModelFile,
            PriorArtEvaluator.file,
            TechnologyEvaluator.file
    };

    public AssigneeEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(assigneeModelFile));
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load market evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();

        System.out.println("Calculating scores for patents...");

        List<Map<String,Double>> evaluators = new ArrayList<>();
        Arrays.stream(files).forEach(file->{
            try {
                Map<String, Double> model = (Map<String, Double>) Database.tryLoadObject(file);
                evaluators.add(model);
            }catch(Exception e) {
                e.printStackTrace();
            }
        });
        Map<String,Double> assigneeModel = new HashMap<>();
        assignees.forEach(assignee->{
            double score = 0.0;
            int count = 0;
            Collection<String> possibleAssignees = Database.possibleNamesForAssignee(assignee);
            for(Map<String,Double> model : evaluators) {
                for(String possibleAssignee: possibleAssignees) {
                    if (model.containsKey(possibleAssignee)) {
                        score += model.get(possibleAssignee);
                    }
                    count++;
                }
            }
            score/=count;
            System.out.println("Score for assignee"+assignee+": "+score);
            assigneeModel.put(assignee,score);
        });
        patents.forEach(patent->{
            double score=0.0;
            int count=0;
            Collection<String> possibleAssignees = Database.assigneesFor(patent);
            for(String possibleAssignee: possibleAssignees) {
                if (assigneeModel.containsKey(possibleAssignee)) {
                    score += assigneeModel.get(possibleAssignee);
                }
                count++;
            }
            score/=count;
            System.out.println("Score for patent "+patent+": "+score);
            assigneeModel.put(patent,score);
        });

        System.out.println("Finished evaluator...");
        return assigneeModel;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<String,Double> map = runModel();
        System.out.println("Finished... Now writing model to file...");
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(assigneeModelFile)));
            oos.writeObject(map);
            oos.flush();
            oos.close();
            System.out.println("Finished successfully.");
        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}
