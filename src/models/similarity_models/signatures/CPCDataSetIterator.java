package models.similarity_models.signatures;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
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
    private CPCHierarchy hierarchy;
    private Random rand = new Random(83);
    private AtomicInteger cursor;
    public CPCDataSetIterator(List<String> assets, boolean shuffle, int batchSize, Map<String,? extends Collection<CPC>> cpcMap, CPCHierarchy hierarchy, int cpcDepth) {
        this.shuffle=shuffle;
        this.cpcMap=cpcMap;
        this.hierarchy=hierarchy;
        this.assets=assets;
        this.batchSize=batchSize;
        this.cursor = new AtomicInteger(0);
        {
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e -> e.getValue().getNumParts() <= cpcDepth).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
            numInputs = cpcToIdxMap.size();
            System.out.println("Input size: "+numInputs);
        }
        System.out.println("Finished splitting test and train.");
        reset();
    }

    private INDArray createVector(Stream<Collection<CPC>> cpcStream) {
        INDArray matrix = Nd4j.create(batchSize,numInputs);
        AtomicInteger batch = new AtomicInteger(0);
        cpcStream.forEach(cpcs->{
            double[] vec = new double[numInputs];
            cpcs.stream().flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream()).filter(cpc->cpcToIdxMap.containsKey(cpc.getName())).distinct().forEach(cpc->{
                int idx = cpcToIdxMap.get(cpc.getName());
                vec[idx] = 1d; //cpc.numSubclasses();
            });
            INDArray a = Nd4j.create(vec);
            //Number norm2 = a.norm2Number();
            //if(norm2.doubleValue()>0) {
            //    a.divi(norm2);
            //} else {
            //    System.out.println("NO NORM!!!");
            //}
            matrix.putRow(batch.get(),a);
            batch.getAndIncrement();
        });
        return matrix;
    }

    @Override
    public DataSet next(int i) {
        INDArray features = createVector(nextCPCStream(i));
        return new DataSet(features,features);
    }

    private synchronized  Stream<Collection<CPC>> nextCPCStream(int num) {
        int c = cursor.getAndAdd(num);
        return assets.subList(c,c+num).parallelStream().map(asset->{
            return cpcMap.get(asset);
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
        cursor.set(0);
        if(shuffle) Collections.shuffle(assets,rand);
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public synchronized int cursor() {
        return cursor.get();
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
        return cursor.get()+batchSize<assets.size();
    }

    @Override
    public DataSet next() {
        return next(batch());
    }
}
