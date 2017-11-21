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
public class NoSaveDataSetManager<T> implements DataSetManager<T> {
    private T rawTrain;
    private T rawTest;
    private T rawVal;
    public NoSaveDataSetManager(T rawTrain, T rawTest, T rawVal) {
        this.rawTrain=rawTrain;
        this.rawTest=rawTest;
        this.rawVal=rawVal;
    }

    public void removeDataFromDisk() {
    }

    public T getTrainingIterator() {
        return rawTrain;
    }

    public T getTrainingIterator(int limit) {
        return rawTrain;
    }

    public T getTestIterator() {
        return rawTest;
    }

    public T getValidationIterator() {
        return rawVal;
    }

    public void saveDataSets() {

    }
}
