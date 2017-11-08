package data_pipeline.models;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/8/17.
 */
public interface TrainablePredictionModel<T> {
    Map<String,T> predict(List<String> assets);
    void train(int nEpochs);
    boolean isSaved();
    void save() throws IOException;
}
