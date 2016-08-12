package analysis;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/25/16.
 */
public class AutoEncoderIterator implements DataSetIterator {
    protected int batchSize;
    protected String query;
    protected SimilarPatentFinder finder;
    protected Iterator<Patent> innerIter;

    public AutoEncoderIterator(int batchSize, SimilarPatentFinder finder) {
        this.batchSize=batchSize;
        this.finder=finder;
        reset();
    }


    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException("totalExamples operation is not available!");
    }

    @Override
    public int inputColumns() {
        return Constants.VECTOR_LENGTH;
    }

    @Override
    public int totalOutcomes() {
        throw new UnsupportedOperationException("totalOutcomes operation is not available!");
    }

    @Override
    public DataSet next(int num) {
        try {
            return nextDataSet(num);
        } catch(Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }


    @Override
    public void reset() {
        innerIter = finder.getPatentList().iterator();
    }

    protected DataSet nextDataSet(int num) throws Exception {
        int i = 0;
        List<Patent> patents = new ArrayList<>(num);
        while(i < num && innerIter.hasNext()) {
            patents.add(innerIter.next());
        }
        INDArray features = Nd4j.create(patents.size(), Constants.VECTOR_LENGTH);
        AtomicInteger incr = new AtomicInteger(0);
        patents.forEach(p->features.putRow(incr.getAndIncrement(),p.getVector().add(1.0).div(2.0)));
        return new DataSet(features,features);
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException("cursor operation is not available!");
    }

    @Override
    public int numExamples() {
        throw new UnsupportedOperationException("numExamples operation is not available!");
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        throw new UnsupportedOperationException("setPreProcessor operation is not available!");
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("getLabels operation is not available!");
    }

    @Override
    public boolean hasNext() {
        return innerIter.hasNext();
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove operation is not available!");
    }


}

