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
public class TFIDFVectorFromTextTransformer implements Function<Collection<String>,INDArray> {
    private Map<String,Integer> wordToIdxMap;
    private Map<String,Integer> docCountMap;
    private double N;
    public TFIDFVectorFromTextTransformer(Map<String,Integer> wordToIdxMap, Map<String,Integer> docCountMap, int totalNumDocuments) {
        this.wordToIdxMap=wordToIdxMap;
        this.docCountMap=docCountMap;
        this.N=totalNumDocuments;
    }

    @Override
    public INDArray apply(Collection<String> tokens) {
        INDArray vec = Nd4j.zeros(wordToIdxMap.size());
        AtomicLong maxFreq = new AtomicLong(0);
        tokens.stream()
                .filter(word->wordToIdxMap.containsKey(word))
                .collect(Collectors.groupingBy(word->word,Collectors.counting()))
                .entrySet().forEach(e->{
                    int idx = wordToIdxMap.get(e.getKey());
                    if(maxFreq.get()<e.getValue()) maxFreq.set(e.getValue());
                    vec.putScalar(idx,e.getValue()*Math.log(Math.E+N/Math.max(1,docCountMap.getOrDefault(e.getKey(),1))));
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
