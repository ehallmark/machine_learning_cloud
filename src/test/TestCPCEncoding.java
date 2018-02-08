package test;

import models.similarity_models.combined_similarity_model.DeepCPC2VecEncodingPipelineManager;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import tools.MinHeap;

import java.util.*;

/**
 * Created by Evan on 2/8/2018.
 */
public class TestCPCEncoding {
    public static void main(String[] args) {
        Map<String,INDArray> map = DeepCPC2VecEncodingPipelineManager.getOrLoadManager(false,false).loadPredictions();

        Set<String> cpcs = new HashSet<>(map.keySet());

        cpcs.forEach(cpc->{
            System.out.println("Closest to "+cpc+": "+String.join("; ",closestLabels(map.get(cpc),map)));
        });
    }

    private static List<String> closestLabels(INDArray cpc, Map<String,INDArray> map) {
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(5);
        map.entrySet().stream().forEach(e->{
            heap.add(new WordFrequencyPair<>(e.getKey(), Transforms.cosineSim(cpc,e.getValue())));
        });
        List<String> results = new ArrayList<>();
        while(!heap.isEmpty()) {
            results.add(0,heap.remove().getFirst());
        }
        return results;
    }
}