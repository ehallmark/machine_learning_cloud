package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.Function3;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestCPCModels extends TestModelHelper {


    private static Map<String,Pair<String,String>> loadData(int n) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Random rand = new Random(235);

        List<CPC> cpcs = new ArrayList<>(hierarchy.getLabelToCPCMap().values());
        Collections.shuffle(cpcs);
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
        // load input data
        final int numSamples = 100000;
        Map<String,Pair<String,String>> cpcData = loadData(numSamples);

        // vae model
        DeepCPCVAEPipelineManager encodingPipelineManager3 = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);

        System.out.println("Num cpc samples used: "+cpcData.size());

        // run tests
        double score3 = test(encodingPipelineManager3, "Model 3",cpcData);

        String testName = "CPC Test";
        tester.scoreModel("Model 3",testName,score3);

        System.out.println("Score for Model 3: "+score3);
    }
}
