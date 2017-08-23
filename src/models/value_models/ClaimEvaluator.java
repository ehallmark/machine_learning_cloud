package models.value_models;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.File;


/**
 * Created by Evan on 1/27/2017.
 */
public class ClaimEvaluator extends ValueAttr {
    static final File claimLengthModelFile = new File(Constants.DATA_FOLDER+"independent_claim_length_value_model.jobj");
    static final File claimRatioModelFile = new File(Constants.DATA_FOLDER+"independent_claim_ratio_value_model.jobj");

    @Override
    public String getName() {
        return Constants.CLAIM_VALUE;
    }

    private static final File[] files = new File[]{
            claimLengthModelFile,
            claimRatioModelFile
    };

    public ClaimEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    private static void runModel(){
        System.out.println("Starting to load claim evaluator...");

        System.out.println("Finished evaluator...");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run claim value model.");
        runModel();
    }
}
