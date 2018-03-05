package models.text_streaming;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 11/19/2017.
 */
public class WordVectorizerAutoEncoderIterator implements DataSetIterator {

    public static final Function<String,Map<String,Integer>> defaultBOWFunction = content -> {
        return Stream.of(content.split(",")).map(str->{
            String[] pair = str.split(":");
            if(pair.length==1) return null;
            return new Pair<>(pair[0],Integer.valueOf(pair[1]));
        }).filter(p->p!=null).collect(Collectors.toMap(p->p.getFirst(), p->p.getSecond()));
    };


    private int batch;
    private LabelAwareIterator documentIterator;
    private Function<Map<String,Integer>,INDArray>  transformer;
    private int numInputs;
    private Function<String,Map<String,Integer>> BOWFunction;
    public WordVectorizerAutoEncoderIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, Function<String,Map<String,Integer>> BOWFunction, boolean binary) {
        this.batch=batch;
        this.documentIterator=documentIterator;
        this.transformer = new BOWVectorFromCountMapTransformer(wordToIdxMap, binary);
        this.numInputs=wordToIdxMap.size();
        this.BOWFunction=BOWFunction;
    }

    public WordVectorizerAutoEncoderIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, Map<String,Integer> docCountMap, int totalNumDocuments, Function<String,Map<String,Integer>> BOWFunction) {
        this.batch=batch;
        this.documentIterator=documentIterator;
        this.transformer = new TFIDFVectorFromCountMapTransformer(wordToIdxMap,docCountMap,totalNumDocuments);
        this.numInputs=wordToIdxMap.size();
        this.BOWFunction=BOWFunction;
    }

    public WordVectorizerAutoEncoderIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, boolean binary) {
        this(batch,documentIterator,wordToIdxMap,defaultBOWFunction,binary);
    }


    public WordVectorizerAutoEncoderIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, Map<String,Integer> docCountMap, int totalNumDocuments) {
        this(batch,documentIterator,wordToIdxMap,docCountMap,totalNumDocuments,defaultBOWFunction);
    }



    @Override
    public boolean hasNext() {
        return documentIterator.hasNext();
    }

    @Override
    public DataSet next(int batch) {
        INDArray mat = Nd4j.create(batch,numInputs);
        int i;
        for(i = 0; i < batch; i++) {
            INDArray vec;
            if(documentIterator.hasNext()) {
                vec = transformer.apply(BOWFunction.apply(documentIterator.next().getContent()));
            } else {
                break;
            }
            mat.putRow(i,vec);
        }
        if(i<batch) {
            mat = mat.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
        }
        return new DataSet(mat,mat);
    }

    @Override
    public DataSet next() {
        return next(batch());
    }

    @Override
    public int totalExamples() {
        return 0;
    }

    @Override
    public int inputColumns() {
        return numInputs;
    }

    @Override
    public int totalOutcomes() {
        return inputColumns();
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        documentIterator.reset();
    }

    @Override
    public int batch() {
        return batch;
    }

    @Override
    public int cursor() {
       throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        return 0;
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
}
