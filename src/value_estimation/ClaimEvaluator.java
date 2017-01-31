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
public class ClaimEvaluator extends Evaluator {
    private static final File claimLengthModelFile = new File("independent_claim_length_value_model.jobj");
    private static final File pendencyModelFile = new File("pendency_value_model.jobj");
    private static final File claimRatioModelFile = new File("independent_claim_ratio_value_model.jobj");
    private static final File[] files = new File[]{
            claimLengthModelFile,
            pendencyModelFile,
            claimRatioModelFile
    };

    public ClaimEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.stream(files).map(file->((Map<String,Double>)Database.tryLoadObject(file))).collect(Collectors.toList());
    }

    private static Map<File,Map<String,Double>> runModel(){
        Map<File,Map<String,Double>> fileToModelMaps = new HashMap<>();
        System.out.println("Starting to load claim evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();

        System.out.println("Calculating scores for patents...");
        // pendency model
        System.out.println("Pendency model...");
        Map<String,Double> pendencyModel = new HashMap<>();
        {
            Map<String,LocalDate> patentToPubDateMap = (Map<String,LocalDate>)Database.tryLoadObject(new File("patent_to_pubdate_map_file.jobj"));
            Map<String,LocalDate> patentToAppDateMap = (Map<String,LocalDate>)Database.tryLoadObject(new File("patent_to_appdate_map_file.jobj"));
            patents.forEach(patent->{
                if(patentToPubDateMap.containsKey(patent)&&patentToAppDateMap.containsKey(patent)) {
                    LocalDate pubDate = patentToPubDateMap.get(patent);
                    LocalDate appDate = patentToAppDateMap.get(patent);
                    int pendency = appDate.until(pubDate).getDays();
                    double score = (double)pendency;
                    System.out.println("Score for patent "+patent+": "+score);
                    pendencyModel.put(patent,score);
                }
            });
        }


        // ind claim length model
        System.out.println("Claim length model...");
        Map<String,Double> indClaimLengthModel = new HashMap<>();
        {
            Map<String,Integer> indClaimLengthMap = (Map<String,Integer>)Database.tryLoadObject(new File("patent_to_independent_claim_length_map.jobj"));
            indClaimLengthMap.forEach((patent,intVal)->{
                indClaimLengthModel.put(patent,(double)intVal);
            });
        }

        // ind claim ratio model
        System.out.println("Claim ratio model...");
        Map<String,Double> indClaimRatioModel = (Map<String,Double>)Database.tryLoadObject(new File("patent_to_independent_claim_ratio_map.jobj"));


        try {
            WeightLookupTable<VocabWord> lookupTable = ParagraphVectorModel.loadParagraphsModel().getLookupTable();
            addScoresToAssigneesFromPatents(assignees, pendencyModel, lookupTable);
            addScoresToAssigneesFromPatents(assignees, indClaimLengthModel, lookupTable);
            addScoresToAssigneesFromPatents(assignees, indClaimRatioModel, lookupTable);
        } catch(Exception e) {
            e.printStackTrace();
        }

        fileToModelMaps.put(pendencyModelFile,pendencyModel);
        fileToModelMaps.put(claimLengthModelFile,indClaimLengthModel);
        fileToModelMaps.put(claimRatioModelFile,indClaimRatioModel);

        System.out.println("Finished evaluator...");
        return fileToModelMaps;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<File,Map<String,Double>> maps = runModel();
        System.out.println("Finished... Now writing model to file...");
        maps.forEach((file,map)->{
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                oos.writeObject(map);
                oos.flush();
                oos.close();
                System.out.println("Finished successfully.");
            }catch(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
