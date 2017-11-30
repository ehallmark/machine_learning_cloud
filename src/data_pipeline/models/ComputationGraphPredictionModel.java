package data_pipeline.models;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;

import java.io.File;
import java.io.IOException;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class ComputationGraphPredictionModel<T> extends BaseTrainablePredictionModel<T,ComputationGraph> {
    protected ComputationGraphPredictionModel(String modelName) {
        super(modelName);
    }

    public abstract File getModelBaseDirectory();

    @Override
    protected void saveNet(ComputationGraph net, File file) throws IOException {
        ModelSerializer.writeModel(net,file,true);
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = ModelSerializer.restoreComputationGraph(modelFile, true);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

}
