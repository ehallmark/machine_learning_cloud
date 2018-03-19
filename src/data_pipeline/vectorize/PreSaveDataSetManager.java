package data_pipeline.vectorize;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public class PreSaveDataSetManager<T extends Iterator> implements DataSetManager<T> {
    private T rawTrain;
    private T rawTest;
    private T rawVal;
    private T fullIter;
    private double testRatio;
    private double valRatio;
    private int miniBatch;
    private File baseDir;
    @Setter @Getter
    private DataSetPreProcessor dataSetPreProcessor;
    @Getter @Setter
    private MultiDataSetPreProcessor multiDataSetPreProcessor;
    private boolean multi;
    public PreSaveDataSetManager(File baseDir, T rawTrain, T rawTest, T rawVal, boolean multi) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.baseDir=baseDir;
        this.rawVal=rawVal;
        this.multi=multi;
    }

    public PreSaveDataSetManager(File baseDir, T fullIter, double testRatio, double valRatio, boolean multi) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.testRatio=testRatio;
        this.multi=multi;
        this.valRatio=valRatio;
        this.baseDir=baseDir;
        this.fullIter=fullIter;
    }

    public PreSaveDataSetManager(File baseDir, int miniBatch, boolean multi) {
        this.baseDir=baseDir;
        this.multi=multi;
        this.miniBatch=miniBatch;
        //if(!this.baseDir.exists()) throw new RuntimeException("Please use other constructor and call saveDatasets()");
    }

    public void removeDataFromDisk() {
        try {
            System.out.println("Removing previous datasets...");
            FileUtils.deleteDirectory(baseDir);
            System.out.println("Finished removing previous datasets.");
        } catch(Exception e) {
            System.out.println("Error removing previous datasets: "+e.getMessage());
        }
        baseDir.mkdir();
    }

    public T getTrainingIterator() {
        return getIterator(TRAIN,miniBatch);
    }

    public T getTrainingIterator(int limit) {
        return getIterator(TRAIN,limit,miniBatch);
    }

    public T getTestIterator() {
        return getIterator(TEST,-1);
    }

    public T getValidationIterator() {
        return getIterator(VALIDATION,-1);
    }

    protected T getIterator(String kind, int limit, int miniBatch) {
        T iterator;
        if(multi) {
            iterator = (T) new FileMultiMinibatchIterator(new File(baseDir,kind),limit,miniBatch,!kind.equals(TRAIN));
            if(multiDataSetPreProcessor!=null) {
                ((MultiDataSetIterator)iterator).setPreProcessor(multiDataSetPreProcessor);
            }
        } else {
            iterator = (T)new FileMinibatchIterator(new File(baseDir,kind),limit,miniBatch);
            if(dataSetPreProcessor!=null) {
                ((DataSetIterator)iterator).setPreProcessor(dataSetPreProcessor);
            }
        }
        return iterator;
    }

    protected T getIterator(String kind, int miniBatch) {
        return getIterator(kind,-1,miniBatch);
    }

    public void saveDataSets() {
        System.out.println("Starting to save datasets...");

        File trainFolder = new File(baseDir, TRAIN);
        if (!trainFolder.exists()) trainFolder.mkdir();

        File testFolder = new File(baseDir, TEST);
        if (!testFolder.exists()) testFolder.mkdir();

        File valFolder = new File(baseDir, VALIDATION);
        if (!valFolder.exists()) valFolder.mkdir();

        if(fullIter== null) {
            // training data
            if(rawTrain instanceof MultiDataSetIterator) {
                iterate((MultiDataSetIterator)rawTest, testFolder);
                iterate((MultiDataSetIterator)rawVal, valFolder);
                iterate((MultiDataSetIterator)rawTrain, trainFolder);

            } else {
                iterate((DataSetIterator)rawTest, testFolder);
                iterate((DataSetIterator)rawVal, valFolder);
                iterate((DataSetIterator) rawTrain, trainFolder);
            }

        } else {
            iterateFull(trainFolder,testFolder,valFolder);
        }
        System.out.println("Finished saving datasets.");
    }

    private void iterate(DataSetIterator iterator, File folder) {
        int idx = 0;
        final int total = iterator.numExamples()/iterator.batch();
        while(iterator.hasNext()) {
            String filename = EXAMPLE+idx+BINARY_SUFFIX;
            DataSet ds = iterator.next();
            if(ds!=null) {
                if(dataSetPreProcessor!=null) {
                    dataSetPreProcessor.preProcess(ds);
                }
                try {
                    ds.save(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(new File(folder, filename)))));
                } catch(Exception e) {
                    e.printStackTrace();
                }
                idx++;
                System.out.println("Saved [" + idx + " / " + total + "] to " + filename);
            }
        }
    }

    private void iterate(MultiDataSetIterator iterator, File folder) {
        int idx = 0;
        while(iterator.hasNext()) {
            String filename = EXAMPLE+idx+BINARY_SUFFIX;
            MultiDataSet ds = iterator.next();
            if(ds!=null) {
                if(multiDataSetPreProcessor!=null) {
                    multiDataSetPreProcessor.preProcess(ds);
                }
                try {
                    ds.save(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(new File(folder, filename)))));
                } catch(Exception e) {
                    e.printStackTrace();
                }
                idx++;
                System.out.println("Saved [" + idx + "] to " + filename);
            }
        }
    }

    private void iterateFull(File trainFolder, File testFolder, File valFolder) {
        int trainIdx = 0;
        int testIdx = 0;
        int valIdx = 0;
        Random rand = new Random(62359);
        while(fullIter.hasNext()) {
            Object ds = fullIter.next();
            if(ds!=null) {
                int idx;
                double r = rand.nextDouble();
                File folder;
                if(r <= testRatio) {
                    // test
                    idx = testIdx;
                    testIdx++;
                    folder = testFolder;
                } else if(r <= testRatio + valRatio) {
                    // validation
                    idx = valIdx;
                    valIdx++;
                    folder = valFolder;
                } else {
                    // train
                    idx = trainIdx;
                    trainIdx++;
                    folder = trainFolder;
                }
                String filename = EXAMPLE+idx+BINARY_SUFFIX;
                if(ds instanceof MultiDataSet) {
                    try {
                        ((MultiDataSet) ds).save(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(new File(folder, filename)))));
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        ((DataSet) ds).save(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(new File(folder, filename)))));
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("Saved [" + idx + "] to " + filename);
            }
        }
    }
}
