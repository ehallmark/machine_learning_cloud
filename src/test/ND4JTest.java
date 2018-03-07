package test;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Created by ehallmark on 12/13/17.
 */
public class ND4JTest {
    public static void main(String[] args) {
        INDArray v1 = Nd4j.create(new double[][]{
                new double[]{1,2,3,4,5},
                new double[]{6,7,8,9,10}
        });
        INDArray v3 = Nd4j.toFlattened('f',v1);
        System.out.println(v3.toString());
    }
}
