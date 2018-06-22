package models.kmeans;

import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.computable_attributes.TechnologyAttribute;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/2/2018.
 */
public class AssetKMeans {
    private static final int maxEpochs = 50;
    private static final double minScore = 0.3;
    private static final int MIN_K = 2;
    private static final int MAX_K = 50;
    private static final int APPROX_PER_GROUP = 100;
    private static final int B = 10;

    private UnitCosineKMeans kMeans;
    private Map<String,INDArray> assetEncodingMap;
    @Setter
    private Function<String,List<String>> techPredictionFunction;
    private Integer k;
    public AssetKMeans(Function<String,List<String>> techPredictionFunction, Map<String,INDArray> assetToEncodingMap, Integer k) {
        this.kMeans = new UnitCosineKMeans();
        this.k=k;
        this.techPredictionFunction = techPredictionFunction;
        this.assetEncodingMap = assetToEncodingMap;
        //System.out.println("Num tech predictions: "+techPredictions.size());
    }

    public Map<String,List<String>> clusterAssets() {
        int minK = k==null?MIN_K:k;
        int maxK = k==null?MAX_K:k;
        int startingK = Math.min(assetEncodingMap.size(),minK);
        int endingK = Math.min(assetEncodingMap.size(),maxK);

        kMeans.optimize(assetEncodingMap,startingK,endingK,B,maxEpochs);

        Map<String,List<String>> map = Collections.synchronizedMap(new HashMap<>());
        List<Set<String>> clusters = kMeans.getClusters();
        System.out.println("Found "+clusters.size()+" clusters.");
        AtomicInteger cnt = new AtomicInteger(0);

        final int keywordSamples = 30;

        Map<Integer,Map<String,Long>> clusterIdxToTagFrequencyMap = new HashMap<>();
        for(int i = 0; i < clusters.size(); i++) {
            Set<String> cluster = clusters.get(i);
            List<String> keywords = cluster.stream().flatMap(asset->techPredictionFunction.apply(asset).stream()).collect(Collectors.toList());
            Map<String,Long> freqMap = keywords.stream().collect(Collectors.groupingBy(keyword->keyword,Collectors.counting()))
                    .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(keywordSamples).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
            clusterIdxToTagFrequencyMap.put(i,freqMap);
        }


        for(int i = 0; i < clusters.size(); i++) {
            Set<String> cluster = clusters.get(i);
            if(cluster.isEmpty()) continue;

            String tag = clusterIdxToTagFrequencyMap.get(i)
                    .entrySet().stream()
                    .map(e->{
                        double tfidf = e.getValue().doubleValue();
                        return new Pair<>(e.getKey(),tfidf);
                    }).max(Comparator.comparing(e->e.getSecond())).orElse(new Pair<>(null,null)).getFirst();




            if(tag==null) tag = "other "+cnt.getAndIncrement();
            tag = TechnologyAttribute.formatTechnologyString(tag);
            if(tag==null) tag = "other "+cnt.getAndIncrement();


            map.putIfAbsent(tag,Collections.synchronizedList(new ArrayList<>()));
            map.get(tag).addAll(cluster);
        }

        System.out.println("Num assets to cluster: "+assetEncodingMap.size());
        return map;
    }

}
