package value_estimation;

import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import seeding.Database;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class AssetsSoldEvaluator extends Evaluator {
    static final File file = new File("assignee_to_assets_sold_ratio_map.jobj");

    public AssetsSoldEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Assets Sold Value");
        setModel();
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(file));
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load citation evaluator...");
        Collection<String> assignees = Database.getAssignees();
        Map<String,Integer> patentToAssetsSoldCountMap = (Map<String,Integer>)Database.tryLoadObject(new File("patent_to_assets_sold_count_map.jobj"));

        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for assignees...");
        assignees.forEach(assignee->{
            if(patentToAssetsSoldCountMap.containsKey(assignee)) {
                int totalAsssets = Database.getExactAssetCountFor(assignee);
                if (totalAsssets > 0) {
                    Double score = new Double(patentToAssetsSoldCountMap.get(assignee))/totalAsssets;
                    model.put(assignee,score);
                }
            }
        });
        System.out.println("Finished evaluator...");
        return model;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<String,Double> map = runModel();
        System.out.println("Finished... Now writing model to file...");
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(map);
        oos.flush();
        oos.close();
        System.out.println("Finished successfully.");
    }
}
