package models.text_streaming;

import com.google.common.util.concurrent.AtomicDouble;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
        AtomicLong cnt = new AtomicLong(0);
        tokens.stream()
                .filter(word->wordToIdxMap.containsKey(word))
                .collect(Collectors.groupingBy(word->word,Collectors.counting()))
                .entrySet().forEach(e->{
                    int idx = wordToIdxMap.get(e.getKey());
                    cnt.getAndAdd(e.getValue());
                    vec.putScalar(idx,e.getValue());
                });
        if(cnt.get()>0) vec.divi(cnt);
        return vec;
    }
}
