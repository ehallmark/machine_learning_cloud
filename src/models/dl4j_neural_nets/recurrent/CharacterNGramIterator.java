package models.dl4j_neural_nets.recurrent;

import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 11/2/2017.
 */
public class CharacterNGramIterator implements DataSetIterator {
    private static final char[] VALID_CHARS = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',' '};
    private static final Map<Character,INDArray> CHAR_TO_VEC_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        Arrays.sort(VALID_CHARS);
        for(int i = 0; i < VALID_CHARS.length; i++) {
            INDArray vec = Nd4j.zeros(VALID_CHARS.length);
            vec.putScalar(i,1);
            CHAR_TO_VEC_MAP.put(VALID_CHARS[i],vec);
        }
    }

    private int k;
    private int batchSize;
    private Iterator<Pair<String,INDArray>> textAndLabelIterator;
    public CharacterNGramIterator(int k, Iterator<Pair<String,INDArray>> textAndLabelIterator, int batchSize) {
        this.k=k;
        this.batchSize=batchSize;
        this.textAndLabelIterator=textAndLabelIterator;
    }

    private static int charIdx(char c) {
        for(int i = 0; i < VALID_CHARS.length; i++) {
            if(VALID_CHARS[i]==c) return i;
        }
        return -1;
    }

    @Override
    public DataSet next(int b) {
        Pair<String,INDArray> textAndLabelPair = textAndLabelIterator.next();
        String text = textAndLabelPair.getFirst().toLowerCase().replaceAll("[^a-z ]"," ");
        List<INDArray> vectors = Collections.synchronizedList(new ArrayList<>());
        for(int i = 0; i < text.length()-k; i+=(k/2)) {
            String sample = text.substring(i,i+k);
            INDArray vec = textToCharVector(sample);
            if(vec!=null) {
                vectors.add(vec);
            }
        }
        if(vectors.isEmpty()) return null;
        INDArray features = Nd4j.create(new int[]{vectors.size(),k,VALID_CHARS.length});
        for(int i = 0; i < vectors.size(); i++) {
            features.put(new INDArrayIndex[]{NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()}, vectors.get(i));
        }
        return new DataSet(features,textAndLabelPair.getSecond());
    }



    public static INDArray textToCharVector(String text) {
        char[] chars = text.toCharArray();
        List<INDArray> vectors = IntStream.range(0,chars.length).mapToObj(i->chars[i]).map(ch->CHAR_TO_VEC_MAP.get(ch)).filter(v->v!=null).map(v->v.dup()).collect(Collectors.toList());
        if(vectors.isEmpty()) return null;
        return Nd4j.vstack(vectors);
    };

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return VALID_CHARS.length;
    }

    @Override
    public int totalOutcomes() {
        return 0;
    }

    @Override
    public boolean resetSupported() {
        return false;
    }

    @Override
    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {

    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        return textAndLabelIterator.hasNext();
    }

    @Override
    public DataSet next() {
        return next(batch());
    }

    public static void main(String[] args) {
        // test
        Iterator<Pair<String,INDArray>> test = Stream.of(
                new Pair<>("here is some text",Nd4j.ones(10)),
                new Pair<>("and more tesxt 029385lkzdjg zzjx weriwht other bad nuaad''a3 chars", Nd4j.zeros(20))
        ).iterator();
        CharacterNGramIterator iter = new CharacterNGramIterator(3,test,10);
        while(iter.hasNext()) {
            DataSet ds = iter.next();
            System.out.println("Features: "+ds.getFeatures().toString());
        }
    }
}
