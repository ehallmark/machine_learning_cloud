package models.text_streaming;

import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Evan on 11/19/2017.
 */
public class BOWAutoEncoderIterator implements Iterator<DataSet> {
    private int batch;
    private Iterator<LabelledDocument> documentIterator;
    private BOWVectorFromTextTransformer transformer;
    private int numInputs;
    private Function<String,Collection<String>> tokenizer;
    public BOWAutoEncoderIterator(int batch, Iterator<LabelledDocument> documentIterator, Map<String,Integer> wordToIdxMap, Function<String,Collection<String>> tokenizer) {
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
    public DataSet next() {
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
}
