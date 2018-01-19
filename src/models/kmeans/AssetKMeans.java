package models.kmeans;

import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.PredictKeyphraseForFilings;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/2/2018.
 */
public class AssetKMeans {
    private static final int maxEpochs = 200;
    private static final double minScore = 0.3;
    private static final int MIN_K = 2;
    private static final int MAX_K = 20;
    private static final int APPROX_PER_GROUP = 100;
    private static final int B = 10;
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();


    private KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager;
    private UnitCosineKMeans kMeans;
    private Map<String,INDArray> assetEncodingMap;
    private Map<String,List<String>> techPredictions;
    public AssetKMeans(Map<String,INDArray> assetToEncodingMap, KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager) {
        this.kMeans = new UnitCosineKMeans();
        this.keyphrasePredictionPipelineManager=keyphrasePredictionPipelineManager;
        this.techPredictions = PredictKeyphraseForFilings.loadOrGetTechnologyMap();
        this.assetEncodingMap = assetToEncodingMap;
        System.out.println("Num tech predictions: "+techPredictions.size());
    }

    public AssetKMeans(List<String> assets, KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager) {
        this(computeEncodingsForAssets(assets,keyphrasePredictionPipelineManager),keyphrasePredictionPipelineManager);
    }

    public static Map<String,INDArray> computeEncodingsForAssets(List<String> assets, KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager) {
        Map<String,INDArray> cpcVectors = keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadCPCVectors();

        return assets.stream().distinct().map(asset->{
            Collection<String> cpcs = Database.classificationsFor(asset);
            List<INDArray> cpcVecs = cpcs.stream().map(cpc->cpcVectors.get(cpc)).filter(vec->vec!=null).collect(Collectors.toList());
            if(cpcVecs.size()>0) {
                INDArray cpcVec = Transforms.unitVec(Nd4j.vstack(cpcVecs).mean(0));
                return new Pair<>(asset,cpcVec);
            }
            else return null;
        }).filter(p->p!=null).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
    }

    public Map<String,List<String>> clusterAssets() {
        int numAssets = assetEncodingMap.size();

        int startingK = Math.max(MIN_K, Math.min(MAX_K/2,numAssets/APPROX_PER_GROUP));
        int endingK = Math.min(MAX_K, Math.max(startingK+1,numAssets/APPROX_PER_GROUP));
        if(numAssets>1000) {
            startingK = MAX_K-1;
            endingK = MAX_K;
        }

        kMeans.optimize(assetEncodingMap,startingK,endingK,B,maxEpochs);

        Map<String,List<String>> map = Collections.synchronizedMap(new HashMap<>());
        List<Set<String>> clusters = kMeans.getClusters();
        System.out.println("Found "+clusters.size()+" clusters.");
        AtomicInteger cnt = new AtomicInteger(0);


        clusters.forEach(cluster->{
            if(cluster.isEmpty()) return;

            Set<String> related = cluster.stream().flatMap(r->{
                return Stream.of(r,assetToFilingMap.getPatentDataMap().getOrDefault(r,assetToFilingMap.getApplicationDataMap().get(r))).filter(f->f!=null);
            }).collect(Collectors.toSet());

            // tag
            String tag = null;
            Collection<String> keywords = related.stream().flatMap(asset->techPredictions.getOrDefault(asset,Collections.emptyList()).stream()).collect(Collectors.toList());
            System.out.println("keywords: "+keywords.size());
            Map<String,INDArray> toPredict = Collections.singletonMap("cluster", Transforms.unitVec(Nd4j.vstack(cluster.stream().map(asset->assetEncodingMap.get(asset)).collect(Collectors.toList())).mean(0)));
            if(keywords.size()>0){
                Map<String,Set<String>> results = keyphrasePredictionPipelineManager.predict(keywords,toPredict,1,minScore);
                if(results.size()>0) {
                    tag = results.values().stream().findAny().orElse(Collections.emptySet()).stream().findAny().orElse(null);
                }
            }
            if(tag==null) tag = "other "+cnt.getAndIncrement();

            map.putIfAbsent(tag,Collections.synchronizedList(new ArrayList<>()));
            map.get(tag).addAll(cluster);
        });

        System.out.println("Num assets to cluster: "+assetEncodingMap.size());
        return map;
    }


    public static void main(String[] args) {
        // test
        final int k = 10;
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.MODEL_NAME,-1,-1,-1);
        KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);
        keyphrasePredictionPipelineManager.runPipeline(false,false,false,false,-1,false);

        List<String> assets = Database.getAllPatentsAndApplications().stream().limit(10000).collect(Collectors.toList());
        AssetKMeans kMeans = new AssetKMeans(assets,keyphrasePredictionPipelineManager);
        Map<String,List<String>> clusters = kMeans.clusterAssets();

        clusters.forEach((name,cluster)->{
            System.out.println("Cluster "+name+": "+String.join(", ",cluster));
        });
    }
}
