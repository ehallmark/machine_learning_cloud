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
import user_interface.ui_models.attributes.computable_attributes.TechnologyAttribute;
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
    private static final int MAX_K = 50;
    private static final int APPROX_PER_GROUP = 100;
    private static final int B = 10;
    private static final int KEYWORD_SAMPLES = 100;
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
        int startingK = Math.min(assetEncodingMap.size(),MIN_K);
        int endingK = Math.min(assetEncodingMap.size(),MAX_K);

        kMeans.optimize(assetEncodingMap,startingK,endingK,B,maxEpochs);

        Map<String,List<String>> map = Collections.synchronizedMap(new HashMap<>());
        List<Set<String>> clusters = kMeans.getClusters();
        System.out.println("Found "+clusters.size()+" clusters.");
        AtomicInteger cnt = new AtomicInteger(0);


        Map<String,Long> overallTagFrequencyMap = new HashMap<>();
        Map<Integer,Map<String,Long>> clusterIdxToTagFrequencyMap = new HashMap<>();
        for(int i = 0; i < clusters.size(); i++) {
            Set<String> cluster = clusters.get(i);

            List<String> related = cluster.stream().flatMap(r->{
                return Stream.of(r,assetToFilingMap.getPatentDataMap().getOrDefault(r,assetToFilingMap.getApplicationDataMap().get(r))).filter(f->f!=null);
            }).collect(Collectors.toList());

            List<String> keywords = related.stream().flatMap(asset->techPredictions.getOrDefault(asset,Collections.emptyList()).stream()).collect(Collectors.toList());

            Map<String,Long> freqMap = keywords.stream().collect(Collectors.groupingBy(keyword->keyword,Collectors.counting()));
            freqMap.keySet().forEach(k->{
                if(overallTagFrequencyMap.containsKey(k)) {
                    overallTagFrequencyMap.put(k,overallTagFrequencyMap.get(k)+1);
                } else {
                    overallTagFrequencyMap.put(k,1L);
                }
            });

            clusterIdxToTagFrequencyMap.put(i,freqMap);
        }


        for(int i = 0; i < clusters.size(); i++) {
            Set<String> cluster = clusters.get(i);
            if(cluster.isEmpty()) continue;

            List<String> keywordSamples = clusterIdxToTagFrequencyMap.get(i)
                    .entrySet().stream().map(e->{
                        double tfidf = e.getValue().doubleValue()*Math.log(1d/overallTagFrequencyMap.get(e.getKey()).doubleValue());
                        return new Pair<>(e.getKey(),tfidf);
                    }).sorted((e1,e2)->e2.getSecond().compareTo(e1.getSecond())).limit(KEYWORD_SAMPLES).map(e->e.getFirst()).collect(Collectors.toList());

            // tag
            String tag = null;
            System.out.println("keywords: "+keywordSamples.size());
            Map<String,INDArray> toPredict = Collections.singletonMap("cluster", Transforms.unitVec(Nd4j.vstack(cluster.stream().map(asset->assetEncodingMap.get(asset)).collect(Collectors.toList())).mean(0)));
            if(keywordSamples.size()>0){
                System.out.println("starting prediction...");
                Map<String,Set<String>> results = keyphrasePredictionPipelineManager.predict(keywordSamples,toPredict,1,minScore);
                if(results.size()>0) {
                    tag = results.values().stream().findAny().orElse(Collections.emptySet()).stream().findAny().orElse(null);
                }
            }
            if(tag==null) tag = "other "+cnt.getAndIncrement();

            tag = TechnologyAttribute.formatTechnologyString(tag);

            map.putIfAbsent(tag,Collections.synchronizedList(new ArrayList<>()));
            map.get(tag).addAll(cluster);
        }

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
            System.out.println("Cluster "+name+": "+cluster.size());
        });
    }
}
