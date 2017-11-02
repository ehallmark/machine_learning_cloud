package models.dl4j_neural_nets.adversarial_nets;

import org.apache.commons.math3.util.Pair;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Created by ehallmark on 11/1/17.
 */
public class AdversaryLossFunction implements ILossFunction {

    public INDArray scoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray output = activationFn.getActivation(preOutput.dup(), true);
        INDArray score = Transforms.log(output,true).muli(labels.rsub(1)).addi(Transforms.log(output.rsub(1)).muli(labels)).negi();
        return score;
    }

    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        INDArray scoreArr = this.computeScoreArray(labels, preOutput, activationFn, mask);
        double score = scoreArr.sumNumber().doubleValue();
        if(average) {
            score /= (double)scoreArr.size(0);
        }

        return score;
    }

    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray scoreArr = this.scoreArray(labels, preOutput, activationFn, mask);
        scoreArr.muli(scoreArr);
        return scoreArr.sum(new int[]{1});
    }

    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray scoreArr = this.scoreArray(labels, preOutput, activationFn, mask);
        INDArray gradients = (INDArray)activationFn.backprop(preOutput, scoreArr).getFirst();
        return gradients;
    }

    public Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        return new Pair(Double.valueOf(this.computeScore(labels, preOutput, activationFn, mask, average)), this.computeGradient(labels, preOutput, activationFn, mask));
    }
}
