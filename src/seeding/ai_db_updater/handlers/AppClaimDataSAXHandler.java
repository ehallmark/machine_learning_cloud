package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import seeding.ai_db_updater.tools.PhrasePreprocessor;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class AppClaimDataSAXHandler extends ClaimDataSAXHandler {
    public static final File appToIndependentClaimLengthFile = new File("app_to_independent_claim_length_map.jobj");
    public static final File appToIndependentClaimRatioFile = new File("app_to_independent_claim_ratio_map.jobj");
    public static final File appToMeansPresentRatioFile = new File("app_to_means_present_ratio_map.jobj");

    protected AppClaimDataSAXHandler(Map<String, Integer> patentToIndependentClaimLengthMap, Map<String, Double> patentToIndependentClaimRatioMap, Map<String, Double> patentToMeansPresentRatioMap) {
        super(patentToIndependentClaimLengthMap, patentToIndependentClaimRatioMap, patentToMeansPresentRatioMap);
    }

    public AppClaimDataSAXHandler() {
        super();
    }

    @Override
    public void save() {
        System.out.println("Saving results...");
        // save maps
        Database.saveObject(patentToIndependentClaimLengthMap, appToIndependentClaimLengthFile);
        Database.saveObject(patentToIndependentClaimRatioMap, appToIndependentClaimRatioFile);
        Database.saveObject(patentToMeansPresentRatioMap, appToMeansPresentRatioFile);
    }

    @Override
    public CustomHandler newInstance() {
        return new AppClaimDataSAXHandler(patentToIndependentClaimLengthMap, patentToIndependentClaimRatioMap, patentToMeansPresentRatioMap);
    }
}