package models.kmeans;

import data_pipeline.helpers.Function2;
import models.NDArrayHelper;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.Function;

/**
 * Created by Evan on 11/27/2017.
 */
public interface DistanceFunctions {
    double EPSILON = 0.0000001;

    Function2<INDArray,INDArray,INDArray> COSINE_DISTANCE_FUNCTION = (v1,v2)-> cosineSimByRow(v1,v2).rsubi(1.0);
    Function2<INDArray,INDArray,INDArray> L1_DISTANCE_FUNCTION = DistanceFunctions::l1SimByRow;
    Function2<INDArray,INDArray,INDArray> L2_DISTANCE_FUNCTION = DistanceFunctions::l2SimByRow;


    static INDArray cosineSimByRow(INDArray m1, INDArray m2) {
        INDArray norms1 = m1.norm2(1);
        INDArray norms2 = m2.norm2(1);
        INDArray dot = m2.mulRowVector(m1).sum(1);
        INDArray norm = norms2.muliRowVector(norms1);
        return dot.divi(norm.addi(EPSILON));
    }

    static INDArray l2SimByRow(INDArray m1, INDArray m2) {
        INDArray diff = Transforms.pow(m2.subRowVector(m1),2,false);
        INDArray sumOfSquareDiffs = diff.sum(1);
        return Transforms.sqrt(sumOfSquareDiffs,false);
    }

    static INDArray l1SimByRow(INDArray m1, INDArray m2) {
        INDArray diff = Transforms.abs(m2.subRowVector(m1),false);
        INDArray sumOfAbsDiffs = diff.sum(1);
        return sumOfAbsDiffs;
    }

}
