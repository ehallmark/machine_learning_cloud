package models.similarity_models.signatures;

import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import tools.MinHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 10/28/2017.
 */
public class TestVectors {
    public static void main(String[] args) {
        CPCSimilarityVectorizer vectorizer = new CPCSimilarityVectorizer(false);

        List<String> assets = vectorizer.getAssetToIdxMap().entrySet().stream().map(e->e.getKey()).limit(20).collect(Collectors.toList());

        INDArray matrix = vectorizer.getMatrix();
        assets.forEach(asset->{
            MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(10);
            INDArray vec = vectorizer.vectorFor(asset);
            if(vec!=null) {
                IntStream.range(0, matrix.rows()).parallel().forEach(i -> {
                    double sim = Transforms.cosineSim(matrix.getRow(i), vec);
                    synchronized (heap) {
                        heap.add(new WordFrequencyPair<>(vectorizer.getIdxToAssetMap().get(i), sim));
                    }
                });
            }

            List<WordFrequencyPair<String,Double>> similar = new ArrayList<>();
            while(!heap.isEmpty()) {
                similar.add(0,heap.remove());
            }
            System.out.println("Similar to "+asset+": "+String.join("; ",similar.stream().map(p->p.getFirst()+" ("+p.getSecond()+")").collect(Collectors.toList())));
        });

    }
}
