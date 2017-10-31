package models.similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Created by ehallmark on 10/31/17.
 */
public interface Vectorizer {
    INDArray vectorFor(String item);
}
