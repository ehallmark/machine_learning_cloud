package models.value_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import seeding.ai_db_updater.handlers.AppClaimDataSAXHandler;
import seeding.ai_db_updater.handlers.ClaimDataSAXHandler;
import tools.DateHelper;
import user_interface.ui_models.attributes.ValueAttr;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class ClaimEvaluator extends ValueAttr {
    static final File claimLengthModelFile = new File("independent_claim_length_value_model.jobj");
    static final File claimRatioModelFile = new File("independent_claim_ratio_value_model.jobj");
    private static final File[] files = new File[]{
            claimLengthModelFile,
            claimRatioModelFile
    };

    public ClaimEvaluator(boolean loadData) {
        super(ValueMapNormalizer.DistributionType.Normal,"Claim Value", loadData);

    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.stream(files).map(file->((Map<String,Double>)Database.tryLoadObject(file))).collect(Collectors.toList());
    }

    private static void runModel(){
        System.out.println("Starting to load claim evaluator...");
        Collection<String> assignees = Database.getAssignees();

        // ind claim length model
        System.out.println("Claim length model...");
        Map<String,Double> indClaimLengthModel = new HashMap<>();
        {
            Map<String,Integer> indClaimLengthMap = new HashMap<>((Map<String,Integer>)Database.tryLoadObject(ClaimDataSAXHandler.patentToIndependentClaimLengthFile));
            Map<String,Integer> indAppClaimLengthMap = (Map<String,Integer>)Database.tryLoadObject(AppClaimDataSAXHandler.appToIndependentClaimLengthFile);
            indClaimLengthMap.putAll(indAppClaimLengthMap);
            INDArray claimLengthVector = Nd4j.create(indClaimLengthMap.size());
            AtomicInteger cnt = new AtomicInteger(0);
            indClaimLengthMap.forEach((patent,intVal)->{
                claimLengthVector.putScalar(cnt.getAndIncrement(),(double)intVal);
            });
            // we actually want to determine the distance from the mean claim length
            final double mean = claimLengthVector.meanNumber().doubleValue();
            indClaimLengthMap.forEach((patent,intVal)->{
                indClaimLengthModel.put(patent,-Math.pow(((double)intVal)-mean,2.0));
            });
        }

        // ind claim ratio model
        System.out.println("Claim ratio model...");
        Map<String,Double> indClaimRatioModel = new HashMap<>((Map<String,Double>)Database.tryLoadObject(ClaimDataSAXHandler.patentToIndependentClaimRatioFile));
        Map<String,Double> appIndClaimRatioModel = (Map<String,Double>)Database.tryLoadObject(AppClaimDataSAXHandler.appToIndependentClaimRatioFile);
        indClaimRatioModel.putAll(appIndClaimRatioModel);


        try {
            DateHelper.addScoresToAssigneesFromPatents(assignees, indClaimLengthModel);
            DateHelper.addScoresToAssigneesFromPatents(assignees, indClaimRatioModel);
        } catch(Exception e) {
            e.printStackTrace();
        }

        Database.trySaveObject(indClaimLengthModel,claimLengthModelFile);
        Database.trySaveObject(indClaimRatioModel,claimRatioModelFile);

        System.out.println("Finished evaluator...");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run claim value model.");
        runModel();
    }
}
