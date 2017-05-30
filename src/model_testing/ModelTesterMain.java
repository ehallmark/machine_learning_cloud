package model_testing;

import ui_models.attributes.classification.*;
import seeding.Database;
import server.SimilarPatentServer;

import java.util.*;

/**
 * Created by Evan on 5/4/2017.
 */
public class ModelTesterMain {
    static SortedMap<String,Double> scoreMap = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        // tests models
        Database.initializeDatabase();
        int numPredictions = 5;

        Map<String,Collection<String>> trainData = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> testData = SplitModelData.getBroadDataMap(SplitModelData.testFile);

        for(String arg : args) {
            for(int i = 1; i <= numPredictions; i+=2) {
                int test = Integer.valueOf(arg);
                if (test == 0) {
                    ClassificationAttr paragraphVectorTagger = new SimilarityGatherTechTagger(trainData, SimilarPatentServer.getLookupTable());
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(paragraphVectorTagger);
                    testModel("Paragraph Vector Simple Average [n=" + i + "]", scorer, testData, i);
                } else if (test == 1) {
                    ClassificationAttr cpcModel = CPCGatherTechTagger.get();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(cpcModel);
                    testModel("CPC Model [n=" + i + "]", scorer, testData, i);
                } else if (test == 2) {
                    ClassificationAttr gatherKeywordModel = new KeywordGatherTechTagger();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(gatherKeywordModel);
                    testModel("Gather Keyword Model [n=" + i + "]", scorer, testData, i);
                } else if (test == 3) {
                    ClassificationAttr svmTagger = GatherSVMClassifier.get();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(svmTagger);
                    testModel("Gather SVM Model [n=" + i + "]", scorer, testData, i);
                } else if (test == 4) {
                    ClassificationAttr bayesTagger = NaiveGatherClassifier.get();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(bayesTagger);
                    testModel("Gather Bayesian Model [n=" + i + "]", scorer, testData, i);
                } else if(test==5) {
                    ClassificationAttr defaultTagger = TechTaggerNormalizer.getDefaultTechTagger();
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(defaultTagger);
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
            return Double.MIN_VALUE;
        }

    }

}
