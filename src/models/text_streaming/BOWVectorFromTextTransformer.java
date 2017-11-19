package models.text_streaming;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/19/2017.
 */
public class BOWVectorFromTextTransformer implements Function<Collection<String>,INDArray> {
    private Map<String,Integer> wordToIdxMap;
    public BOWVectorFromTextTransformer(Map<String,Integer> wordToIdxMap) {
        this.wordToIdxMap=wordToIdxMap;
    }

    @Override
    public INDArray apply(Collection<String> tokens) {
        INDArray vec = Nd4j.zeros(wordToIdxMap.size());
        tokens.stream()
                .filter(word->wordToIdxMap.containsKey(word))
                .collect(Collectors.groupingBy(word->word,Collectors.counting()))
                .entrySet().forEach(e->{
                    int idx = wordToIdxMap.get(e.getKey());
                    vec.putScalar(idx,e.getValue());
                });
        return vec;
    }
}
