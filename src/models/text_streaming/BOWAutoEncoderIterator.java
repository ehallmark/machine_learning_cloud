package models.text_streaming;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Evan on 11/19/2017.
 */
public class BOWAutoEncoderIterator implements DataSetIterator {
    private int batch;
    private LabelAwareIterator documentIterator;
    private BOWVectorFromTextTransformer transformer;
    private int numInputs;
    private Function<String,Collection<String>> tokenizer;
    public BOWAutoEncoderIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, Function<String,Collection<String>> tokenizer) {
        this.batch=batch;
        this.documentIterator=documentIterator;
        this.transformer = new BOWVectorFromTextTransformer(wordToIdxMap);
        this.numInputs=wordToIdxMap.size();
        this.tokenizer=tokenizer;
    }
    @Override
    public boolean hasNext() {
        return documentIterator.hasNext();
    }

    @Override
    public DataSet next(int batch) {
        INDArray mat = Nd4j.create(batch,numInputs);
        for(int i = 0; i < batch; i++) {
            INDArray vec;
            if(documentIterator.hasNext()) {
                vec = transformer.apply(tokenizer.apply(documentIterator.next().getContent()));
            } else {
                vec = Nd4j.zeros(numInputs);
            }
            mat.putRow(i,vec);
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
