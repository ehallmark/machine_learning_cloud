package models.similarity_models.combined_similarity_model;

import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Created by Evan on 12/31/2017.
 */
public interface RecurrentModel  {
    void train(INDArray features, INDArray labels, INDArray featuresMask, INDArray labelsMask);
}
