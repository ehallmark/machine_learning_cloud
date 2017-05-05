package model_testing;

import analysis.genetics.keyword_analysis.GatherKeywordTechTagger;
import analysis.tech_tagger.BuildCPCToGatherStatistics;
import analysis.tech_tagger.CPCTagger;
import seeding.Database;
import server.SimilarPatentServer;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 5/4/17.
 */
public class TrainModelsWithLatestGatherData {
    public static void main(String[] args) throws Exception{
        Database.initializeDatabase();
//        SimilarPatentServer.loadLookupTable();

        Map<String,Collection<String>> trainingData = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Collection<String>> testingData = SplitModelData.getGatherTechnologyTestDataMap();
        Map<String,Collection<String>> validationData = SplitModelData.getGatherTechnologyValidationDataMap();

        // train gather keyword model
//        GatherKeywordTechTagger.trainAndSaveLatestModel(trainingData,testingData,validationData);


        // train gather cpc model
        CPCTagger.trainAndSaveLatestModel(trainingData,testingData,validationData);



    }
}
