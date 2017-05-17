package model_testing;

import graphical_models.classification.NaiveGatherClassifier;
import svm.GatherSVMClassifier;
import ui_models.attributes.classification.CPCGatherTechTagger;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.KeywordGatherTechTagger;
import seeding.Database;
import server.SimilarPatentServer;
import ui_models.attributes.classification.SimilarityGatherTechTagger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 5/4/2017.
 */
public class ModelTesterMain {
    static Map<String,Double> scoreMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // tests models
        Database.initializeDatabase();
        int numPredictions = 5;

        Map<String,Collection<String>> trainData = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Collection<String>> testData = SplitModelData.getGatherTechnologyTestDataMap();
        // test classification (cpc) based model
        ClassificationAttr gatherKeywordModel = new KeywordGatherTechTagger();
        // test classification (cpc) based model
        ClassificationAttr cpcModel = new CPCGatherTechTagger();
        // test paragraph vector basic similarity model
        ClassificationAttr paragraphVectorTagger = new SimilarityGatherTechTagger(trainData, SimilarPatentServer.getLookupTable());
        ClassificationAttr svmTagger = GatherSVMClassifier.load();
        ClassificationAttr bayesTagger = new NaiveGatherClassifier();
        for(int i = 1; i <= numPredictions; i+=2) {
            {

                GatherTechnologyScorer scorer = new GatherTechnologyScorer(paragraphVectorTagger);
                testModel("Paragraph Vector Simple Average [n="+i+"]", scorer, testData, i);
            }
            {
                GatherTechnologyScorer scorer = new GatherTechnologyScorer(cpcModel);
                testModel("CPC Model [n="+i+"]", scorer, testData, i);
            }
            {

                GatherTechnologyScorer scorer = new GatherTechnologyScorer(gatherKeywordModel);
                testModel("Gather Keyword Model [n="+i+"]", scorer, testData, i);
            }
            {

                GatherTechnologyScorer scorer = new GatherTechnologyScorer(svmTagger);
                testModel("Gather SVM Model [n="+i+"]", scorer, testData, i);
            }
            {
                GatherTechnologyScorer scorer = new GatherTechnologyScorer(bayesTagger);
                testModel("Gather Bayesian Modele [n="+i+"]", scorer, testData, i);
            }
        }

        // Report all scores
        scoreMap.forEach((modelName,modelAccuracy) ->{
            System.out.println("Accuracy for Model ("+modelName+")"+": "+modelAccuracy);
        });
    }

    public static void testModel(String modelName, GatherTechnologyScorer scorer, Map<String,Collection<String>> testData, int numPredictions) {
        System.out.println("Starting to test Model: "+modelName);
        double modelAccuracy = scorer.accuracyOn(testData,numPredictions);
        String result = "Accuracy for Model ("+modelName+")"+": "+modelAccuracy;
        System.out.println(result);
        scoreMap.put(modelName,modelAccuracy);
    }

}
