package models.similarity_models.combined_similarity_model;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.*;
import java.util.stream.Collectors;

public class TestSimModels extends TestModelHelper {

    private static Map<String,Pair<String,String>> loadFilingCPCData(Map<String,Set<String>> patentCpcMap, int numFilings) {
        Map<String,String> filingToAssetMap = Collections.synchronizedMap(new HashMap<>());
        patentCpcMap.keySet().parallelStream().forEach(k->{
            String filing = new AssetToFilingMap().getPatentDataMap().get(k);
            if(filing!=null) {
                filingToAssetMap.put(filing,k);
            }
        });
        Map<String,Set<String>> filingCpcMap = filingToAssetMap.entrySet().parallelStream()
                .collect(Collectors.toMap(e->e.getKey(),e->patentCpcMap.get(e.getValue())));

        Map<String,Set<String>> cpcToFilingMap = Collections.synchronizedMap(new HashMap<>());
        filingCpcMap.forEach((filing,cpcs)->{
            cpcs.forEach(cpc->{
                cpcToFilingMap.putIfAbsent(cpc,Collections.synchronizedSet(new HashSet<>()));
                cpcToFilingMap.get(cpc).add(filing);

            });
        });

        Random rand = new Random(23);

        List<String> filings = new ArrayList<>(filingCpcMap.keySet());
        Collections.shuffle(filings, new Random(2352));
        Map<String,Pair<String,String>> data = Collections.synchronizedMap(new HashMap<>(numFilings));
        for(int i = 0; i < Math.min(filings.size(),numFilings); i++) {
            String filing = filings.get(i);
            Set<String> cpcs = filingCpcMap.get(filing);
            List<String> cpcList = new ArrayList<>(cpcs);
            String randomCpc = cpcList.get(rand.nextInt(cpcList.size()));

            Set<String> otherFilings = new HashSet<>(cpcToFilingMap.get(randomCpc));
            otherFilings.remove(filing);

            if(otherFilings.isEmpty()) continue;

            List<String> otherFilingsList = new ArrayList<>(otherFilings);

            String positiveSample = otherFilingsList.get(rand.nextInt(otherFilingsList.size()));

            String negativeSample = filings.get(rand.nextInt(filings.size()));

            data.put(filing, new Pair<>(positiveSample,negativeSample));
        }
        return data;
    }


    public static void runTest(Tester tester) {
        String testName = "Similarity Test";

        // load input data
        final int numFilingSamples = 100000;
        Map<String,Pair<String,String>> data = loadFilingCPCData(new AssetToCPCMap().getPatentDataMap(), numFilingSamples);

        double randomScore = testModel(data,RANDOM_MODEL);
        System.out.println("Random score: "+randomScore);
        tester.scoreModel("Random",testName,randomScore);

        // vae model
        DeepCPCVAEPipelineManager encodingPipelineManagerDeep = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);

        //DeeperCPCVAEPipelineManager encodingPipelineManagerDeeper = new DeeperCPCVAEPipelineManager(DeeperCPCVAEPipelineManager.MODEL_NAME);

        // run tests
        double deepScore = test(encodingPipelineManagerDeep, "Deep Model",data);
       // double deeperScore = test(encodingPipelineManagerDeeper, "Deeper Model",data);
        tester.scoreModel("Deep Model",testName,deepScore);
       // tester.scoreModel("Deeper Model",testName,deeperScore);
        System.out.println();
        System.out.println("Num samples used: "+data.size());
        System.out.println("Score for Deep Model: "+deepScore);
       // System.out.println("Score for Deeper Model: "+deeperScore);
    }
}
