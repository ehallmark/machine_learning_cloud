package test;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Created by ehallmark on 12/13/17.
 */
public class ND4JTest {
    public static void main(String[] args) throws Exception {
        INDArray v1 = Nd4j.rand(10,10);
        Transforms.tanh(v1);
    }
}
