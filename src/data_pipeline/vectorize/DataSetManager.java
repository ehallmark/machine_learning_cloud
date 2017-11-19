package data_pipeline.vectorize;

import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public interface DataSetManager {
    String TRAIN = "training";
    String TEST = "testing";
    String VALIDATION = "validation";
    String EXAMPLE = "dataset-";
    String BINARY_SUFFIX = ".bin";

    void removeDataFromDisk();

    DataSetIterator getTrainingIterator();

    DataSetIterator getTrainingIterator(int limit);

    DataSetIterator getTestIterator();

    DataSetIterator getValidationIterator();

    void saveDataSets();

}
