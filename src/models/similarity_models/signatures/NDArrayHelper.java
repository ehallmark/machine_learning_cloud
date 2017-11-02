package models.similarity_models.signatures;

import org.nd4j.linalg.api.buffer.DoubleBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.RandomOp;
import org.nd4j.linalg.api.ops.ScalarOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastCopyOp;
import org.nd4j.linalg.api.ops.impl.broadcast.BroadcastMulOp;
import org.nd4j.linalg.api.rng.distribution.impl.NormalDistribution;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.stream.IntStream;

/**
 * Created by ehallmark on 10/31/17.
 */
public class NDArrayHelper {
    public static double sumOfCosineSimByRow(INDArray m1, INDArray m2) {
        INDArray norms1 = m1.norm2(1);
        INDArray norms2 = m2.norm2(1);
        INDArray dot = m1.mul(m2).sum(1);
        INDArray norm = norms1.muli(norms2);
        INDArray cosineSim = dot.divi(norm);
        return cosineSim.sumNumber().doubleValue();
    }

    public static INDArray createProbabilityVectorFromGaussian(INDArray in) {
        NormalDistribution distribution = new NormalDistribution(0,1);
        int[] shape = in.shape();
        double[] data = in.data().asDouble();
        IntStream.range(0,data.length).parallel().forEach(i->{
            data[i] = distribution.cumulativeProbability(data[i]);
        });
        return Nd4j.create(data,shape);
    }

    public static void main(String[] args) {
        // test
        INDArray vec = Nd4j.create(new double[][]{
                new double[]{
                        0, -1, 2, 5, 0.1, -0.0001, 0.5, -2
                }, new double[] {
                        0, -1, 2, 5, 0.1, -0.0001, 0.5, -2
                }
        });

        INDArray prob = createProbabilityVectorFromGaussian(vec);

        System.out.println(prob);
    }
}
