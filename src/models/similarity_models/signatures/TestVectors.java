package models.similarity_models.signatures;

import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import tools.MinHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/28/2017.
 */
public class TestVectors {
    public static void main(String[] args) {
        CPCSimilarityVectorizer vectorizer = new CPCSimilarityVectorizer(false, true, false);

        List<String> assets = Database.getCopyOfAllPatents().stream().limit(20).collect(Collectors.toList());

        assets.forEach(asset->{
            List<WordFrequencyPair<String,Double>> similar = vectorizer.similarTo(asset, 10);
            System.out.println("Similar to "+asset+": "+String.join("; ",similar.stream().map(p->p.getFirst()+" ("+p.getSecond()+")").collect(Collectors.toList())));
        });

    }
}
