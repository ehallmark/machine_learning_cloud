package models.similarity_models.signatures;

import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import tools.MinHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 10/28/2017.
 */
public class TestVectors {
    public static void main(String[] args) {
        Map<String,INDArray> lookupTable = BuildSimilarityVectors.getLookupTable();

        List<String> assets = lookupTable.entrySet().stream().map(e->e.getKey()).limit(20).collect(Collectors.toList());


        assets.forEach(asset->{
            MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(10);
            INDArray vec = lookupTable.get(asset);
            lookupTable.entrySet().parallelStream().forEach(e->{
                heap.add(new WordFrequencyPair<>(e.getKey(), Transforms.cosineSim(e.getValue(),vec)));
            });
            List<WordFrequencyPair<String,Double>> similar = new ArrayList<>();
            while(!heap.isEmpty()) {
                similar.add(0,heap.remove());
            }
            System.out.println("Similar to "+asset+": "+String.join("; ",similar.stream().map(p->p.getFirst()+" ("+p.getSecond()+")").collect(Collectors.toList())));
        });

    }
}
