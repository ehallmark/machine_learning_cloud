package models.text_streaming;

import models.similarity_models.Vectorizer;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Evan on 11/19/2017.
 */
public class BOWToCPCVectorIterator implements DataSetIterator{
    private int batch;
    protected LabelAwareIterator documentIterator;
    protected BOWVectorFromTextTransformer transformer;
    protected int numInputs;
    protected Function<String,Collection<String>> tokenizer;
    protected Vectorizer vectorizer;
    protected int numOutputs;
    public BOWToCPCVectorIterator(int batch, LabelAwareIterator documentIterator, Map<String,Integer> wordToIdxMap, Function<String,Collection<String>> tokenizer, Vectorizer vectorizer, int numOutputs) {
        this.batch=batch;
        this.vectorizer=vectorizer;
        this.numOutputs=numOutputs;
        this.documentIterator=documentIterator;
        this.transformer = new BOWVectorFromTextTransformer(wordToIdxMap);
        this.numInputs=wordToIdxMap==null?0:wordToIdxMap.size();
        this.tokenizer=tokenizer;
    }
    @Override
    public boolean hasNext() {
        return documentIterator.hasNext();
    }

    @Override
    public DataSet next(int batch) {
        INDArray mat = Nd4j.create(batch,numInputs);
        INDArray lab = Nd4j.create(batch,numOutputs);
        int i;
        for(i = 0; i < batch; i++) {
            INDArray vec;
            INDArray y;
            LabelledDocument doc = documentIterator.next();
            if(documentIterator.hasNext()) {
                vec = transformer.apply(tokenizer.apply(doc.getContent()));
                y = vectorizer.vectorFor(doc.getLabels().get(0));
                if(y!=null) {
                    mat.putRow(i, vec);
                    lab.putRow(i, y);
                }
            } else {
                break;
            }
        }
        if(i<batch) {
            mat = mat.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
            lab = lab.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
        }
        return new DataSet(mat,lab);
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
        return numOutputs;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return true;
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
