package models.kmeans;

import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/2/2018.
 */
public class AssetKMeans {
    private static final int maxEpochs = 200;
    private static final double minScore = 0.3;

    private KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager;
    private UnitCosineKMeans kMeans;
    private Map<String,INDArray> assetEncodingMap;
    private Map<String,Set<String>> techPredictions;
    private int k;
    public AssetKMeans(int k, Map<String,INDArray> assetToEncodingMap, KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager) {
        this.kMeans = new UnitCosineKMeans();
        this.k=k;
        this.keyphrasePredictionPipelineManager=keyphrasePredictionPipelineManager;
        this.techPredictions = keyphrasePredictionPipelineManager.loadPredictions();
        this.assetEncodingMap = assetToEncodingMap;
    }

    public AssetKMeans(int k, List<String> assets, KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager) {
        Map<String,INDArray> cpcVectors = keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadCPCVectors();
        this.assetEncodingMap = assets.stream().distinct().map(asset->{
            Collection<String> cpcs = Database.classificationsFor(asset);
            List<INDArray> cpcVecs = cpcs.stream().map(cpc->cpcVectors.get(cpc)).filter(vec->vec!=null).collect(Collectors.toList());
            if(cpcVecs.size()>0) {
                INDArray cpcVec = Transforms.unitVec(Nd4j.vstack(cpcVecs).mean(0));
                return new Pair<>(asset,cpcVec);
            }
            else return null;
        }).filter(p->p!=null).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
        this.kMeans = new UnitCosineKMeans();
        this.k=k;
        this.keyphrasePredictionPipelineManager=keyphrasePredictionPipelineManager;
        this.techPredictions = keyphrasePredictionPipelineManager.loadPredictions();
    }

    public Map<String,List<String>> clusterAssets() {
        kMeans.fit(assetEncodingMap,maxEpochs,k);
        Map<String,List<String>> map = Collections.synchronizedMap(new HashMap<>());
        List<Set<String>> clusters = kMeans.getClusters();
        System.out.println("Found "+clusters.size()+" clusters.");
        AtomicInteger cnt = new AtomicInteger(0);
        clusters.forEach(cluster->{
            if(cluster.isEmpty()) return;

            // tag
            String tag = null;
            Collection<String> keywords = cluster.stream().flatMap(asset->techPredictions.getOrDefault(asset,Collections.emptySet()).stream()).collect(Collectors.toList());
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
        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.MODEL_NAME,-1,-1,-1);
        KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);
        keyphrasePredictionPipelineManager.runPipeline(false,false,false,false,-1,false);

        List<String> assets = Database.getAllPatentsAndApplications().stream().limit(10000).collect(Collectors.toList());
        AssetKMeans kMeans = new AssetKMeans(k,assets,keyphrasePredictionPipelineManager);
        Map<String,List<String>> clusters = kMeans.clusterAssets();

        clusters.forEach((name,cluster)->{
            System.out.println("Cluster "+name+": "+String.join(", ",cluster));
        });
    }
}
