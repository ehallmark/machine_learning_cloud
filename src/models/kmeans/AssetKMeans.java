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
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();


    private UnitCosineKMeans kMeans;
    private Map<String,INDArray> assetEncodingMap;
    private Map<String,List<String>> techPredictions;
    private Integer k;
    public AssetKMeans(Map<String,INDArray> assetToEncodingMap, Integer k) {
        this.kMeans = new UnitCosineKMeans();
        this.techPredictions = PredictKeyphraseForFilings.loadOrGetTechnologyMap();
        this.assetEncodingMap = assetToEncodingMap;
        System.out.println("Num tech predictions: "+techPredictions.size());
    }

    public AssetKMeans(List<String> assets, Map<String,INDArray> cpcVectors, Integer k) {
        this(computeEncodingsForAssets(assets,cpcVectors), k);
    }

    public static Map<String,INDArray> computeEncodingsForAssets(List<String> assets, Map<String,INDArray> cpcVectors) {

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
        int minK = k==null?MIN_K:k;
        int maxK = k==null?MAX_K:(k+1);
        int startingK = Math.min(assetEncodingMap.size(),minK);
        int endingK = Math.min(assetEncodingMap.size(),maxK);

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

            String tag = clusterIdxToTagFrequencyMap.get(i)
                    .entrySet().stream().map(e->{
                        double tfidf = e.getValue().doubleValue()*Math.log(1d/overallTagFrequencyMap.get(e.getKey()).doubleValue());
                        return new Pair<>(e.getKey(),tfidf);
                    }).max(Comparator.comparing(e->e.getSecond())).orElse(new Pair<>(null,null)).getFirst();


            // tag

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
        AssetKMeans kMeans = new AssetKMeans(assets,keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadCPCVectors(),null);
        Map<String,List<String>> clusters = kMeans.clusterAssets();

        clusters.forEach((name,cluster)->{
            System.out.println("Cluster "+name+": "+cluster.size());
        });
    }
}
