package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class TestTextModels extends TestModelHelper {
    public static double testModel(Map<String,Pair<String[],String>> cpcToTextAndNegMap, Function<String,INDArray> cpcModel, Function<String[],INDArray> textModel) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicDouble sum = new AtomicDouble(0d);
        cpcToTextAndNegMap.forEach((cpc,pair)->{
            String[] text = pair.getFirst();
            String neg = pair.getSecond();
            INDArray posVec = textModel.apply(text);
            INDArray negVec = cpcModel.apply(neg);
            INDArray vec = cpcModel.apply(cpc);
            if(posVec!=null&&negVec!=null&&vec!=null) {
                double score = Transforms.cosineSim(vec,posVec) > Transforms.cosineSim(vec,negVec) ? 1d : 0d;
                sum.getAndAdd(score);
            }
            cnt.getAndIncrement();
            if(cnt.get()%100==99) {
                System.out.print("-");
            }
            if(cnt.get()%1000==999) {
                System.out.print(" "+sum.get()/cnt.get());
                System.out.println();
            }
        });
        return sum.get()/cnt.get();
    }


    public static void runTest(Tester tester) {
        String testName = "Text Similarity Test";
        int limit = 100000;

        // load input data
        Map<String,Pair<String[],String>> cpcToTextAndNegMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();
        List<String> cpcs = new ArrayList<>(cpcToTitleMap.keySet());
        Collections.shuffle(cpcs);
        Random rand = new Random(23);
        cpcs.stream().limit(limit).forEach(cpc->{
            String text = cpcToTitleMap.get(cpc);
            if(text!=null) {
                String[] words = text.toLowerCase().split("\\s+");
                if(words.length>0) {
                    cpcToTextAndNegMap.put(cpc,new Pair<>(words,cpcs.get(rand.nextInt(cpcs.size()))));
                }
            }
        });

        System.out.println("Num cpcs found: "+cpcToTextAndNegMap.size());

        // random model
        Function<String,INDArray> randCpcModel = str -> Nd4j.randn(1,32);
        Function<String[],INDArray> randTextModel = str -> Nd4j.randn(1,32);

        double randomScore = testModel(cpcToTextAndNegMap, randCpcModel, randTextModel);
        System.out.println("Random score: " + randomScore);
        tester.scoreModel("Random",testName,randomScore);
        // new model
        CombinedCPC2Vec2VAEEncodingPipelineManager encodingPipelineManager1 = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
        encodingPipelineManager1.runPipeline(false,false,false,false,-1,false);
        CombinedCPC2Vec2VAEEncodingModel encodingModel1 = (CombinedCPC2Vec2VAEEncodingModel)encodingPipelineManager1.getModel();
        Map<String,INDArray> allPredictions1 = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME).loadPredictions();

        Function<String,INDArray> cpcModel1 = str -> allPredictions1.get(str);
        Function<String[],INDArray> textModel1 = str -> encodingModel1.encodeText(Arrays.asList(str),1);

        double score1 = testModel(cpcToTextAndNegMap, cpcModel1, textModel1);
        System.out.println("Score for model 3: " + score1);
        tester.scoreModel("Model 3",testName,score1);
    }
}
