package model_testing;

import analysis.genetics.keyword_analysis.GatherKeywordTechTagger;
import analysis.tech_tagger.CPCTagger;
import analysis.tech_tagger.SimilarityTechTagger;
import analysis.tech_tagger.TechTagger;
import seeding.Database;
import server.SimilarPatentServer;
import tools.Emailer;

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
        SimilarPatentServer.loadLookupTable();
        int numPredictions = 3;
        Map<String,Collection<String>> trainData = (Map<String,Collection<String>>) Database.tryLoadObject(SplitModelData.trainFile);
        Map<String,Collection<String>> testData = (Map<String,Collection<String>>) Database.tryLoadObject(SplitModelData.testFile);
        {
            // test paragraph vector basic similarity model
            TechTagger paragraphVectorTagger = new SimilarityTechTagger(trainData, SimilarPatentServer.getLookupTable());
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(paragraphVectorTagger);
            testModel("Paragraph Vector Simple Average",scorer,testData,numPredictions);
        }
        {
            // test classification (cpc) based model
            TechTagger cpcModel = new CPCTagger();
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(cpcModel);
            testModel("CPC Model (trained on whole data set so may be biased)",scorer,testData,numPredictions);
        }


        {
            // test classification (cpc) based model
            TechTagger gatherKeywordModel = new GatherKeywordTechTagger();
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(gatherKeywordModel);
            testModel("Gather Keyword Model (trained on whole data set so may be biased)",scorer,testData,numPredictions);
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
