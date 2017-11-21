package data_pipeline.models;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class Word2VecPredictionModel<T> extends BaseTrainablePredictionModel<T,Word2Vec> {
    protected Word2VecPredictionModel(String modelName) {
        super(modelName);
    }

    public abstract Map<String,T> predict(List<String> assets, List<String> assignees);

    public abstract void train(int nEpochs);

    public abstract File getModelBaseDirectory();

    @Override
    protected void saveNet(Word2Vec net, File file) throws IOException {
        WordVectorSerializer.writeWord2VecModel(net,file);
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = WordVectorSerializer.readWord2VecModel(modelFile);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

}
