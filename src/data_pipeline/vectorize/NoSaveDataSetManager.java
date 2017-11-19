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
public class NoSaveDataSetManager implements DataSetManager {
    private DataSetIterator rawTrain;
    private DataSetIterator rawTest;
    private DataSetIterator rawVal;
    public NoSaveDataSetManager(DataSetIterator rawTrain, DataSetIterator rawTest, DataSetIterator rawVal) {
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.rawVal=rawVal;
    }

    public void removeDataFromDisk() {
    }

    public DataSetIterator getTrainingIterator() {
        return rawTrain;
    }

    public DataSetIterator getTrainingIterator(int limit) {
        return rawTrain;
    }

    public DataSetIterator getTestIterator() {
        return rawTest;
    }

    public DataSetIterator getValidationIterator() {
        return rawVal;
    }

    public void saveDataSets() {

    }
}
