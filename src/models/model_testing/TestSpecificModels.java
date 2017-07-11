package models.model_testing;

import models.classification_models.*;
import seeding.Database;

import java.util.*;

/**
 * Created by Evan on 5/4/2017.
 */
public class TestSpecificModels {
    static SortedMap<String,Double> scoreMap = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        // tests models
        Database.initializeDatabase();
        int numPredictions = 5;
        Map<String,Collection<String>> testData = SplitModelData.getBroadDataMap(SplitModelData.testFile);
        if(args.length==0) args = new String[]{"0","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20"};
        for(String arg : args) {
            for(int i = 1; i <= numPredictions; i+=2) {
                int testIdx=0;
                int test = Integer.valueOf(arg);
                if (test == (testIdx++)) {
                    ClassificationAttr tagger = SimilarityGatherTechTagger.getParagraphVectorModel();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("Paragraph Vector Simple Average [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = SimilarityGatherTechTagger.getCPCModel();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("CPC Similarity Vector Simple Average [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = SimilarityGatherTechTagger.getWIPOMOdel();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("WIPO Similarity Vector Simple Average [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = GatherSVMClassifier.getParagraphVectorModel();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("Gather SVM P-Vector Model [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = GatherSVMClassifier.getCPCModel();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("Gather SVM CPC Model [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = NaiveGatherClassifier.get();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("Gather Bayesian CPC Model [n=" + i + "]", scorer, testData, i);
                } else if (test == (testIdx++)) {
                    ClassificationAttr tagger = TechTaggerNormalizer.getDefaultTechTagger();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(tagger);
                    testModel("Combined Model [n=" + i + "]", scorer, testData, i);
                }
            }
        }

        // Report all scores
        scoreMap.forEach((modelName,modelAccuracy) ->{
            System.out.println("Accuracy for Model ("+modelName+")"+": "+modelAccuracy);
        });
    }

    public static double testModel(String modelName, GatherTechnologyScorer scorer, Map<String,Collection<String>> testData, int numPredictions) {
        try {
            System.out.println("Starting to test Model: " + modelName);
            double modelAccuracy = scorer.accuracyOn(testData, numPredictions);
            String result = "Accuracy for Model (" + modelName + ")" + ": " + modelAccuracy;
            System.out.println(result);
            scoreMap.put(modelName, modelAccuracy);
            return modelAccuracy;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error on: "+modelName+" ["+numPredictions+"]");
            return 0d;
        }

    }

}
