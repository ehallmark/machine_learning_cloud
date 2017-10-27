package models.similarity_models.signatures;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class CPCDataSetIterator implements DataSetIterator {
    private boolean shuffle;
    private List<String> assets;
    private int batchSize;
    private Map<String,Integer> cpcToIdxMap;
    private Map<String,? extends Collection<CPC>> cpcMap;
    private int numInputs;
    private Random rand = new Random(83);
    @Getter
    private Stream<INDArray> stream;
    private Iterator<INDArray> iterator;
    public CPCDataSetIterator(List<String> assets, boolean shuffle, int batchSize, Map<String,? extends Collection<CPC>> cpcMap, Map<String,Integer> cpcToIdxMap) {
        this.shuffle=shuffle;
        this.cpcMap=cpcMap;
        this.assets=assets;
        this.batchSize=batchSize;
        this.cpcToIdxMap = cpcToIdxMap;
        this.numInputs=cpcToIdxMap.size();
        reset();
    }

    private INDArray createVector(Stream<Collection<CPC>> cpcStream) {
        AtomicInteger batch = new AtomicInteger(0);
        double[][] vecs = new double[batchSize][numInputs];
        cpcStream.parallel().forEach(cpcs->{
            double[] vec = new double[numInputs];
            cpcs.forEach(cpc->{
                int idx = cpcToIdxMap.get(cpc.getName());
                vec[idx] = 1d;
            });
            INDArray a = Nd4j.create(vec);
            Number norm2 = a.norm2Number();
            if(norm2.doubleValue()>0) {
                a.divi(norm2);
            } else {
                System.out.println("NO NORM!!!");
            }
            vecs[batch.get()] = vec;
            batch.getAndIncrement();
        });
        return Nd4j.create(vecs);
    }

    @Override
    public DataSet next(int i) {
        INDArray features = iterator.next();
        return new DataSet(features,features);
    }

    private Stream<INDArray> getCPCStreams() {
        return IntStream.range(0,assets.size()/batchSize).parallel().mapToObj(i->{
            int idx = i*batchSize;
            INDArray vector = createVector(assets.subList(idx,idx+batchSize).stream().map(asset->{
                return cpcMap.get(asset);
            }));
            return vector;
        });
    }

    @Override
    public int totalExamples() {
        return assets.size();
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
        return true;
    }

    @Override
    public void reset() {
        if(shuffle) Collections.shuffle(assets,rand);
        stream = getCPCStreams();
        iterator = getCPCStreams().iterator();
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException("cursor()");
    }

    @Override
    public int numExamples() {
        return totalExamples();
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
        return null;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DataSet next() {
        return next(batch());
    }
}
