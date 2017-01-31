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
public class MarketEvaluator extends Evaluator {
    static final File transactionValueModelFile = new File("transaction_value_model.jobj");
    static final File assetFamilyValueModelFile = new File("asset_family_value_model.jobj");
    static final File maintenanceFeeValueModelFile = new File("maintenance_fee_value_model.jobj");
    private static final File[] files = new File[]{
            transactionValueModelFile,
            assetFamilyValueModelFile,
            maintenanceFeeValueModelFile
    };

    public MarketEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.stream(files).map(file->((Map<String,Double>)Database.tryLoadObject(file))).collect(Collectors.toList());
    }

    private static Map<File,Map<String,Double>> runModel(){
        Map<File,Map<String,Double>> fileToModelMaps = new HashMap<>();
        System.out.println("Starting to load market evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();

        System.out.println("Calculating scores for patents...");
        // pendency model
        System.out.println("Asset family model...");
        // ind claim length model
        Map<String,Double> assetFamilyModel = new HashMap<>();
        {
            Map<String,Set<String>> relatedDocsMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_related_docs_map_file.jobj"));
            relatedDocsMap.forEach((patent,relatedAssets)->{
                assetFamilyModel.put(patent,(double)(relatedAssets.size()));
            });
        }


        // ind claim length model
        System.out.println("Maintenance fee Model...");
        Map<String,Double> maintenanceFeeModel = new HashMap<>();

        // ind claim ratio model
        System.out.println("Transaction Model...");
        Map<String,Double> transactionModel = new HashMap<>();


        try {
            WeightLookupTable<VocabWord> lookupTable = ParagraphVectorModel.loadParagraphsModel().getLookupTable();
            addScoresToAssigneesFromPatents(assignees, assetFamilyModel, lookupTable);
            addScoresToAssigneesFromPatents(assignees, maintenanceFeeModel, lookupTable);
            addScoresToAssigneesFromPatents(assignees, transactionModel, lookupTable);
        } catch(Exception e) {
            e.printStackTrace();
        }

        fileToModelMaps.put(assetFamilyValueModelFile,assetFamilyModel);
        fileToModelMaps.put(maintenanceFeeValueModelFile,maintenanceFeeModel);
        fileToModelMaps.put(transactionValueModelFile,transactionModel);

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
