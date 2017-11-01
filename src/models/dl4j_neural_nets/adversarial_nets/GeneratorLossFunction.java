package models.dl4j_neural_nets.adversarial_nets;

import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.conditions.Conditions;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossUtil;

/**
 * Created by ehallmark on 11/1/17.
 */
public class GeneratorLossFunction implements ILossFunction {
    public INDArray scoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray output = activationFn.getActivation(preOutput.dup(), true);
        INDArray scoreArr = output.muli(labels);
        scoreArr.rsubi(Double.valueOf(1.0D));
        if(mask != null) {
            LossUtil.applyMask(scoreArr, mask);
        }

        return scoreArr;
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
        BooleanIndexing.replaceWhere(scoreArr, Double.valueOf(0.0D), Conditions.lessThan(Double.valueOf(0.0D)));
        scoreArr.muli(scoreArr);
        return scoreArr.sum(new int[]{1});
    }

    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray scoreArr = this.scoreArray(labels, preOutput, activationFn, mask);
        INDArray bitMaskRowCol = scoreArr.dup();
        BooleanIndexing.replaceWhere(bitMaskRowCol, Double.valueOf(0.0D), Conditions.lessThan(Double.valueOf(0.0D)));
        BooleanIndexing.replaceWhere(bitMaskRowCol, Double.valueOf(1.0D), Conditions.greaterThan(Double.valueOf(0.0D)));
        INDArray dLda = scoreArr.muli(Integer.valueOf(2)).muli(labels.neg());
        dLda.muli(bitMaskRowCol);
        if(mask != null && LossUtil.isPerOutputMasking(dLda, mask)) {
            LossUtil.applyMask(dLda, mask);
        }

        INDArray gradients = (INDArray)activationFn.backprop(preOutput, dLda).getFirst();
        if(mask != null) {
            LossUtil.applyMask(gradients, mask);
        }

        return gradients;
    }

    public Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        return new Pair(Double.valueOf(this.computeScore(labels, preOutput, activationFn, mask, average)), this.computeGradient(labels, preOutput, activationFn, mask));
    }
}
