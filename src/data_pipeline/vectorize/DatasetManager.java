package data_pipeline.vectorize;

import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import org.deeplearning4j.datasets.iterator.AsyncDataSetIterator;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.ExistingMiniBatchDataSetIterator;
import org.nd4j.linalg.dataset.MiniBatchFileDataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.io.Serializable;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DatasetManager {
    private static final String TRAIN = "training";
    private static final String TEST = "testing";
    private static final String VALIDATION = "validation";
    private static final String EXAMPLE = "dataset-";
    private static final String BINARY_SUFFIX = ".bin";
    private DataSetIterator rawTrain;
    private DataSetIterator rawTest;
    private DataSetIterator rawVal;
    private DataSetIterator fullIter;
    private double testRatio;
    private double valRatio;
    private File baseDir;
    public DatasetManager(File baseDir, DataSetIterator rawTrain, DataSetIterator rawTest, DataSetIterator rawVal) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.baseDir=baseDir;
        this.rawVal=rawVal;
    }

    public DatasetManager(File baseDir, DataSetIterator fullIter, double testRatio, double valRatio) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.testRatio=testRatio;
        this.valRatio=valRatio;
        this.baseDir=baseDir;
        this.fullIter=fullIter;
    }

    public DatasetManager(File baseDir) {
        this.baseDir=baseDir;
        if(!this.baseDir.exists()) throw new RuntimeException("Please use other constructor and call saveDatasets()");
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

    public DataSetIterator getTrainingIterator() {
        return getIterator(TRAIN);
    }

    public DataSetIterator getTrainingIterator(int limit) {
        return getIterator(TRAIN,limit);
    }

    public DataSetIterator getTestIterator() {
        return getIterator(TEST);
    }

    public DataSetIterator getValidationIterator() {
        return getIterator(VALIDATION);
    }

    protected DataSetIterator getIterator(String kind, int limit) {
        return new FileMinibatchIterator(new File(baseDir,kind),limit);
    }

    protected DataSetIterator getIterator(String kind) {
        return getIterator(kind,-1);
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
            iterate(rawTrain, trainFolder);
            iterate(rawTest, testFolder);
            iterate(rawVal, valFolder);
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
                ds.save(new File(folder, filename));
                idx++;
                System.out.println("Saved [" + idx + " / " + total + "] to " + filename);
            }
        }
    }

    private void iterateFull(File trainFolder, File testFolder, File valFolder) {
        int idx = 0;
        final int total = fullIter.numExamples()/fullIter.batch();
        Random rand = new Random(62359);
        while(fullIter.hasNext()) {
            String filename = EXAMPLE+idx+BINARY_SUFFIX;
            DataSet ds = fullIter.next();
            if(ds!=null) {
                double r = rand.nextDouble();
                File folder;
                if(r < testRatio) {
                    // test
                    folder = testFolder;
                } else if(r < testRatio + valRatio) {
                    // validation
                    folder = valFolder;
                } else {
                    // train
                    folder = trainFolder;
                }
                ds.save(new File(folder, filename));
                idx++;
                System.out.println("Saved [" + idx + " / " + total + "] to " + filename);
            }
        }
    }
}
