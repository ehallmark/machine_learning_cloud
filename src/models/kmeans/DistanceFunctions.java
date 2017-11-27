package models.kmeans;

import data_pipeline.helpers.Function2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.Function;

/**
 * Created by Evan on 11/27/2017.
 */
public interface DistanceFunctions {

    Function2<INDArray,INDArray,Double> COSINE_DISTANCE_FUNCTION = (v1,v2)->  1.0 - Transforms.cosineSim(v1,v2);
    Function2<INDArray,INDArray,Double> L1_DISTANCE_FUNCTION = Transforms::manhattanDistance;
    Function2<INDArray,INDArray,Double> L2_DISTANCE_FUNCTION = Transforms::euclideanDistance;


}
