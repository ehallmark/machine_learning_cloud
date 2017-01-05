package tools;

import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Created by ehallmark on 9/29/16.
 */
public interface DistanceFunction {
    double distance(INDArray v1, INDArray v2);
}
