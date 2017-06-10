package dl4j_neural_nets.listeners;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

/**
 * Created by ehallmark on 12/7/16.
 */
public class CustomAutoEncoderListener implements IterationListener {
    private int printIterations = 1000;
    private boolean invoked = false;
    private long iterCount = 0L;

    public CustomAutoEncoderListener(int printIterations) {
        this.printIterations=printIterations;
    }


    public boolean invoked() {
        return this.invoked;
    }

    public void invoke() {
        this.invoked = true;
    }
    @Override
    public void iterationDone(Model model, int i) {
        if(this.printIterations <= 0) {
            this.printIterations = 1;
        }

        if(this.iterCount % (long)this.printIterations == 0L) {
            this.invoke();
            double result = model.score();
            System.out.println("Score at iteration " + this.iterCount + " is " + result);
        }

        ++this.iterCount;

    }

}
