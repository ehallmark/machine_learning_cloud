package models.kmeans;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

public class MatrixMultiplication {
    public static INDArray multiplyInBatches(INDArray x, INDArray y, int batchSize) {
        INDArray res = Nd4j.create(x.rows(),y.columns());
        multiplyInBatchesHelper(x,y,batchSize,0,res);
        return res;
    }

    private static void multiplyInBatchesHelper(INDArray x, INDArray y, int batchSize, int startIdx, INDArray res) {
        if(x.rows()<=batchSize) {
            res.get(NDArrayIndex.interval(startIdx,startIdx+x.rows()),NDArrayIndex.all()).assign(x.mmul(y));
        } else {
            INDArray xPart = x.get(NDArrayIndex.interval(0,batchSize),NDArrayIndex.all());
            INDArray xEnd = x.get(NDArrayIndex.interval(batchSize,x.rows()),NDArrayIndex.all());
            multiplyInBatchesHelper(xPart,y,batchSize,startIdx,res);
            multiplyInBatchesHelper(xEnd,y,batchSize,startIdx+batchSize,res);
        }
    }
}
