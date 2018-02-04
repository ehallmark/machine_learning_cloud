package data_pipeline.vectorize;

import data_pipeline.helpers.ShuffleArray;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FileMultiMinibatchIterator implements MultiDataSetIterator{
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    private final String pattern;
    private int[] shuffledIndices;

    private MultiDataSetPreProcessor dataSetPreProcessor;
    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {
        this.dataSetPreProcessor=multiDataSetPreProcessor;
    }

    private final List<RecursiveTask<List<MultiDataSet>>> dataSetQueue;
    private Iterator<MultiDataSet> currentIterator;
    private int miniBatch;


    public FileMultiMinibatchIterator(File rootDir, int limit, int miniBatch) {
        this(rootDir, DEFAULT_PATTERN, limit, miniBatch);
    }

    public FileMultiMinibatchIterator(File rootDir, String pattern, int limit, int miniBatch) {
        this.totalBatches = -1;
        this.rootDir = rootDir;
        int numFiles = rootDir.list().length;
        this.totalBatches = limit > 0 ? Math.min(limit,numFiles) : numFiles;
        this.pattern = pattern;
        this.dataSetQueue = new ArrayList<>();
        this.currIdx = new AtomicInteger(0);
        this.miniBatch=miniBatch;
        this.shuffledIndices=new int[totalBatches];
        for(int i = 0; i < totalBatches; i++) {
            shuffledIndices[i]=i;
        }
        ShuffleArray.shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public MultiDataSet next(int num) {
        throw new UnsupportedOperationException("Unable to load custom number of examples");
    }

    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    public int inputColumns() {
        throw new UnsupportedOperationException();
    }

    public int totalOutcomes() {
        throw new UnsupportedOperationException();
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return false;
    }

    public void reset() {
        this.currIdx.set(0);
        ShuffleArray.shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public int batch() {
        throw new UnsupportedOperationException();
    }

    public int cursor() {
        return this.currIdx.get();
    }

    public int numExamples() {
        throw new UnsupportedOperationException();
    }


    public List<String> getLabels() {
        return null;
    }

    public boolean hasNext(){
        int nWorkers = 2;
        while(dataSetQueue.size()<nWorkers && this.currIdx.get()<shuffledIndices.length) {
            final int readIdx = shuffledIndices[this.currIdx.getAndIncrement()];
            RecursiveTask<List<MultiDataSet>> task = new RecursiveTask<List<MultiDataSet>>() {
                @Override
                protected List<MultiDataSet> compute() {
                   // System.gc();
                    try {
                        MultiDataSet e = read(readIdx);
                        if (dataSetPreProcessor != null) {
                            dataSetPreProcessor.preProcess(e);
                        }

                        // split
                        if(miniBatch>0) {
                            return IntStream.range(0, e.getFeatures(0).shape()[0]/miniBatch).mapToObj(i->{
                                int start = i*miniBatch;
                                int end = Math.min(e.getFeatures(0).rows(),start+miniBatch);
                                if(start<end) {
                                    INDArray[] features = e.getFeatures().clone();
                                    for(int j = 0; j < features.length; j++) {
                                        int nDims = features[j].shape().length;
                                        INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                        indices[0] = NDArrayIndex.interval(start,end);
                                        for(int k = 1; k < indices.length; k++) {
                                            indices[k] = NDArrayIndex.all();
                                        }
                                        features[j]=features[j].get(indices);
                                    }
                                    INDArray[] labels = e.getLabels().clone();
                                    for(int j = 0; j < labels.length; j++) {
                                        int nDims = labels[j].shape().length;
                                        INDArrayIndex[] indices = new INDArrayIndex[nDims];
                                        indices[0] = NDArrayIndex.interval(start,end);
                                        for(int k = 1; k < indices.length; k++) {
                                            indices[k] = NDArrayIndex.all();
                                        }
                                        labels[j]=labels[j].get(indices);
                                    }
                                    return new org.nd4j.linalg.dataset.MultiDataSet(
                                            features,
                                            labels
                                    );
                                }
                                return null;
                            }).filter(d->d!=null).collect(Collectors.toList());
                        } else {
                            return Collections.singletonList(e);
                        }
                    } catch (Exception var2) {
                        var2.printStackTrace();
                        System.out.println("Reading file: "+readIdx);
                        throw new IllegalStateException("Unable to read dataset");
                    }
                }
            };
            task.fork();

            dataSetQueue.add(task);
        }
        return (currentIterator!=null&&currentIterator.hasNext())||dataSetQueue.size()>0;
    }

    public MultiDataSet next() {
        return nextDataSet();
    }

    private MultiDataSet nextDataSet() {
        if(currentIterator==null||!currentIterator.hasNext()) {
            currentIterator = dataSetQueue.remove(0).join().iterator();
        }
        return currentIterator.next();
    }

    private MultiDataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        MultiDataSet d =new org.nd4j.linalg.dataset.MultiDataSet();
        d.load(path);
        return d;
    }
}
