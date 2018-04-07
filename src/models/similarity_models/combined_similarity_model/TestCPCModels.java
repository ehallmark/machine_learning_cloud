package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.stream.IntStream;

public class TestCPCModels extends TestModelHelper {


    public static Map<String,Pair<String,String>> loadData(int n) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Random rand = new Random(235);

        List<CPC> cpcs = new ArrayList<>(hierarchy.getLabelToCPCMap().values());
        cpcs.sort((c1,c2)->c1.getName().compareTo(c2.getName()));
        Collections.shuffle(cpcs,rand);

        Map<String,Pair<String,String>> data = Collections.synchronizedMap(new HashMap<>());
        IntStream.range(0,Math.min(n,cpcs.size())).parallel().forEach(i->{
            CPC cpc = cpcs.get(i);
            CPC parent = cpc.getParent();
            if(parent==null) {
                return;
            }
            List<CPC> children = new ArrayList<>(parent.getChildren());
            children.remove(cpc);
            if(children.isEmpty()) {
                return;
            }
            CPC randChild = children.get(rand.nextInt(children.size()));
            CPC randNeg = cpcs.get(rand.nextInt(cpcs.size()));
            Pair<String,String> pair = new Pair<>(randChild.getName(),randNeg.getName());
            data.put(cpc.getName(),pair);
        });
        return data;
    }

    public static void runTest(Tester tester) {
        String testName = "CPC Test";
        // load input data
        final int numSamples = 100000;
        Map<String,Pair<String,String>> data = loadData(numSamples);

        double randomScore = testModel(data,RANDOM_MODEL);
        System.out.println("Random score: "+randomScore);
        tester.scoreModel("Random",testName,randomScore);
        System.out.println("Num cpc samples used: "+data.size());

        // vae model
        DeepCPCVAEPipelineManager encodingPipelineManagerDeep = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);

        //DeeperCPCVAEPipelineManager encodingPipelineManagerDeeper = new DeeperCPCVAEPipelineManager(DeeperCPCVAEPipelineManager.MODEL_NAME);

        // run tests
        double deepScore = test(encodingPipelineManagerDeep, "Deep Model",data);
       // double deeperScore = test(encodingPipelineManagerDeeper, "Deeper Model",data);
        tester.scoreModel("Deep Model",testName,deepScore);
        //tester.scoreModel("Deeper Model",testName,deeperScore);
        System.out.println();
        System.out.println("Num samples used: "+data.size());
        System.out.println("Score for Deep Model: "+deepScore);
       // System.out.println("Score for Deeper Model: "+deeperScore);
    }
}
