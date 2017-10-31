package models.similarity_models.signatures;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Created by ehallmark on 10/31/17.
 */
public class NDArrayHelper {
    private static final double ROOT_2_PI = Math.sqrt(2d*Math.PI);
    public static double sumOfCosineSimByRow(INDArray m1, INDArray m2) {
        INDArray norms1 = m1.norm2(1);
        INDArray norms2 = m2.norm2(1);
        INDArray dot = m1.mul(m2).sum(1);
        INDArray norm = norms1.muli(norms2);
        INDArray cosineSim = dot.divi(norm);
        return cosineSim.sumNumber().doubleValue();
    }

    public static INDArray createProbabilityVectorFromGaussian(INDArray in) {
        return Transforms.exp(Transforms.pow(in,2,true).negi().divi(2),false).divi(ROOT_2_PI);
    }

    public static void main(String[] args) {
        // test
        INDArray vec = Nd4j.create(new double[][]{
                new double[]{
                        0, -1, 2, 5, 0.1, -0.0001, 0.5
                }, new double[] {
                        0, -1, 2, 5, 0.1, -0.0001, 0.5
                }
        });

        INDArray prob = createProbabilityVectorFromGaussian(vec);

        System.out.println(prob);
    }
}
