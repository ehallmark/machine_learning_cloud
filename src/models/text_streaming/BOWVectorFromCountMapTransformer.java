package models.text_streaming;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/19/2017.
 */
public class BOWVectorFromCountMapTransformer implements Function<Map<String,Integer>,INDArray> {
    private Map<String,Integer> wordToIdxMap;
    public BOWVectorFromCountMapTransformer(Map<String,Integer> wordToIdxMap) {
        this.wordToIdxMap=wordToIdxMap;
    }

    @Override
    public INDArray apply(Map<String,Integer> tokenCounts) {
        INDArray vec = Nd4j.zeros(wordToIdxMap.size());
        AtomicLong cnt = new AtomicLong(0);
        tokenCounts.entrySet().forEach(e->{
                    int idx = wordToIdxMap.get(e.getKey());
                    cnt.getAndAdd(e.getValue());
                    vec.putScalar(idx,e.getValue());
                });
        if(cnt.get()>0) vec.divi(cnt);
        return vec;
    }
}
