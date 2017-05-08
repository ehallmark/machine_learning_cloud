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
        int numPredictions = 5;

        Map<String,Collection<String>> trainData = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Collection<String>> testData = SplitModelData.getGatherTechnologyTestDataMap();
        // test classification (cpc) based model
        TechTagger gatherKeywordModel = new GatherKeywordTechTagger();
        // test classification (cpc) based model
        TechTagger cpcModel = new CPCTagger();
        // test paragraph vector basic similarity model
        TechTagger paragraphVectorTagger = new SimilarityTechTagger(trainData, SimilarPatentServer.getLookupTable());
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
