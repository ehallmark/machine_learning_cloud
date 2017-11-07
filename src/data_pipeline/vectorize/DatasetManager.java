package data_pipeline.vectorize;

import models.dl4j_neural_nets.iterators.datasets.AsyncDataSetIterator;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.dataset.ExistingMiniBatchDataSetIterator;
import org.nd4j.linalg.dataset.MiniBatchFileDataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.io.Serializable;

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
    private File baseDir;
    public DatasetManager(File baseDir, DataSetIterator rawTrain, DataSetIterator rawTest, DataSetIterator rawVal) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.baseDir=baseDir;
        this.rawVal=rawVal;
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

    public DataSetIterator getTestIterator() {
        return getIterator(TEST);
    }

    public DataSetIterator getValidationIterator() {
        return getIterator(VALIDATION);
    }

    protected DataSetIterator getIterator(String kind) {
        return new AsyncDataSetIterator(new ExistingMiniBatchDataSetIterator(new File(baseDir,kind)), Runtime.getRuntime().availableProcessors()/2);
    }

    public void saveDataSets() {
        System.out.println("Starting to save datasets...");

        // training data
        File trainFolder = new File(baseDir,TRAIN);
        if(!trainFolder.exists()) trainFolder.mkdir();
        iterate(rawTrain,trainFolder);

        File testFolder = new File(baseDir,TEST);
        if(!testFolder.exists()) testFolder.mkdir();
        iterate(rawTest,testFolder);

        File valFolder = new File(baseDir,VALIDATION);
        if(!valFolder.exists()) valFolder.mkdir();
        iterate(rawVal,valFolder);

        System.out.println("Finished saving datasets.");
    }

    private void iterate(DataSetIterator iterator, File folder) {
        int idx = 0;
        final int total = iterator.numExamples()/iterator.batch();
        while(iterator.hasNext()) {
            String filename = EXAMPLE+idx+BINARY_SUFFIX;
            iterator.next().save(new File(folder, filename));
            idx++;
            System.out.println("Saved ["+idx+" / "+total+"] to "+filename);
        }
    }
}
