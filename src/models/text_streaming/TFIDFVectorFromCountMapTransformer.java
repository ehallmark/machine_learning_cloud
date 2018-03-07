package models.text_streaming;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Created by Evan on 11/19/2017.
 */
public class TFIDFVectorFromCountMapTransformer implements Function<Map<String,Integer>,INDArray> {
    private Map<String,Integer> wordToIdxMap;
    private double N;
    private Map<String,Integer> docCountMap;
    public TFIDFVectorFromCountMapTransformer(Map<String,Integer> wordToIdxMap, Map<String,Integer> docCountMap, int totalNumDocuments) {
        this.wordToIdxMap=wordToIdxMap;
        this.N=totalNumDocuments;
        this.docCountMap=docCountMap;
    }

    @Override
    public INDArray apply(Map<String,Integer> tokenCounts) {
        INDArray vec = Nd4j.zeros(wordToIdxMap.size());
        AtomicLong maxFreq = new AtomicLong(0);
        tokenCounts.entrySet().forEach(e->{
                    if(maxFreq.get()<e.getValue()) maxFreq.set(e.getValue());
                    Integer idx = wordToIdxMap.get(e.getKey());
                    if(idx!=null) {
                        vec.putScalar(idx,e.getValue()*Math.log(Math.E+N/Math.max(1,docCountMap.getOrDefault(e.getKey(),1))));
                    }
                });
        if(maxFreq.get()>0) {
            vec.divi(maxFreq);
        }
        Number l2 = vec.norm2Number();
        if(l2.doubleValue()>0) {
            vec.divi(l2);
        }
        return vec;
    }
}
