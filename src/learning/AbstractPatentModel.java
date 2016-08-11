package learning;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by ehallmark on 7/25/16.
 */
public abstract class AbstractPatentModel {
    protected MultiLayerNetwork model;
    protected int batchSize;
    protected int iterations;
    protected List<String> oneDList;
    protected List<String> twoDList;
    protected DataSetIterator iter;
    protected DataSetIterator test;

    public AbstractPatentModel(DataSetIterator iter, DataSetIterator test, int batchSize, int iterations, int numEpochs, List<String> oneDList, List<String> twoDList, File toSaveModel) throws Exception {
        this.batchSize=batchSize;
        this.iterations=iterations;
        this.oneDList=oneDList;
        this.twoDList=twoDList;
        this.iter = iter;
        this.test = test;
        model=buildModel();
        model.setListeners(new ScoreIterationListener(10));
        fitModel(numEpochs);
        saveModel(toSaveModel);
    }

    public AbstractPatentModel(DataSetIterator iter, DataSetIterator test, int batchSize, int iterations, int numEpochs, File toSaveModel) throws Exception {
        this(iter, test, batchSize, iterations, numEpochs, Constants.DEFAULT_1D_VECTORS, Constants.DEFAULT_2D_VECTORS, toSaveModel);
    }

    protected void fitModel(int numEpochs) {
        System.out.println("Train model...");
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Epoch #: "+(i+1));
            model.fit(iter);
            iter.reset();


            Evaluation evaluation = new Evaluation(iter.totalOutcomes());
            while(test.hasNext()){
                DataSet t = test.next();
                INDArray labels = t.getLabels();
                INDArray predicted = model.output(t.getFeatureMatrix(),false);
                assert labels.columns()==predicted.columns() && labels.rows()==predicted.rows();
                assert labels.sumNumber().doubleValue() >= 1.0;
                evaluation.eval(labels,predicted);
            }
            test.reset();
            System.out.println(evaluation.stats());
        }
    }

    protected abstract MultiLayerNetwork buildModel();

    protected void saveModel(File toSave) throws IOException {
        ModelSerializer.writeModel(model, toSave, true);
    }
}
