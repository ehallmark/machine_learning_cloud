package models.dl4j_neural_nets.listeners;

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
public class CustomClassificationModelListener implements IterationListener {
    private int printIterations = 1000;
    private boolean invoked = false;
    private long iterCount = 0L;
    private MultiLayerNetwork net;
    private boolean timeSeries;
    private DataSetIterator test;

    public CustomClassificationModelListener(MultiLayerNetwork net, DataSetIterator test, boolean timeSeries) {
        this.test=test;
        this.timeSeries=timeSeries;
        this.net=net;
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
            Evaluation evaluation = new Evaluation();
            while (test.hasNext()) {
                DataSet t = test.next();
                INDArray features = t.getFeatureMatrix();
                INDArray lables = t.getLabels();
                INDArray predicted = net.output(features, false);
                if(timeSeries) {
                    INDArray lMask = t.getLabelsMaskArray();
                    evaluation.evalTimeSeries(lables, predicted, lMask);
                } else {
                    evaluation.eval(lables,predicted);
                }
            }
            test.reset();

            System.out.println(evaluation.stats());
        }

        ++this.iterCount;

    }

}
