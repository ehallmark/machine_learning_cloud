package data_pipeline.models;

import lombok.Getter;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class NeuralNetworkPredictionModel<T> extends BaseTrainablePredictionModel<T,MultiLayerNetwork> {
    protected NeuralNetworkPredictionModel(String modelName) {
        super(modelName);
    }

    public abstract Map<String,T> predict(List<String> assets, List<String> assignees);

    public abstract void train(int nEpochs);

    public abstract File getModelBaseDirectory();

    @Override
    protected void saveNet(MultiLayerNetwork net, File file) throws IOException {
        ModelSerializer.writeModel(net,file,true);
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = ModelSerializer.restoreMultiLayerNetwork(modelFile, true);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

}
