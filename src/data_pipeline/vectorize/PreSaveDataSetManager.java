package data_pipeline.vectorize;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public class PreSaveDataSetManager implements DataSetManager<DataSetIterator> {
    private DataSetIterator rawTrain;
    private DataSetIterator rawTest;
    private DataSetIterator rawVal;
    private DataSetIterator fullIter;
    private double testRatio;
    private double valRatio;
    private File baseDir;
    @Setter @Getter
    private DataSetPreProcessor dataSetPreProcessor;
    public PreSaveDataSetManager(File baseDir, DataSetIterator rawTrain, DataSetIterator rawTest, DataSetIterator rawVal) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.baseDir=baseDir;
        this.rawVal=rawVal;
    }

    public PreSaveDataSetManager(File baseDir, DataSetIterator fullIter, double testRatio, double valRatio) {
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException("Must be a directory...");
        this.testRatio=testRatio;
        this.valRatio=valRatio;
        this.baseDir=baseDir;
        this.fullIter=fullIter;
    }

    public PreSaveDataSetManager(File baseDir) {
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
        FileMinibatchIterator iterator = new FileMinibatchIterator(new File(baseDir,kind),limit);
        if(dataSetPreProcessor!=null) {
            iterator.setDataSetPreProcessor(dataSetPreProcessor);
        }
        return iterator;
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
                if(dataSetPreProcessor!=null) {
                    dataSetPreProcessor.preProcess(ds);
                }
                ds.save(new File(folder, filename));
                idx++;
                System.out.println("Saved [" + idx + " / " + total + "] to " + filename);
            }
        }
    }

    private void iterateFull(File trainFolder, File testFolder, File valFolder) {
        int trainIdx = 0;
        int testIdx = 0;
        int valIdx = 0;
        final int total = fullIter.numExamples()/fullIter.batch();
        Random rand = new Random(62359);
        while(fullIter.hasNext()) {
            DataSet ds = fullIter.next();
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
                ds.save(new File(folder, filename));
                System.out.println("Saved [" + idx + " / " + total + "] to " + filename);
            }
        }
    }
}
