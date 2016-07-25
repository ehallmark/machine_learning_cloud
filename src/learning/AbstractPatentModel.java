package learning;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
    public AbstractPatentModel(int batchSize, int iterations, List<String> oneDList, List<String> twoDList, File toSaveModel) throws Exception {
        this.batchSize=batchSize;
        this.iterations=iterations;
        this.oneDList=oneDList;
        this.twoDList=twoDList;
        model=buildAndFitModel();
        saveModel(toSaveModel);
    }

    public AbstractPatentModel(int batchSize, int iterations, File toSaveModel) throws Exception {
        this(batchSize, iterations, Arrays.asList(Constants.ABSTRACT_VECTORS, Constants.DESCRIPTION_VECTORS,Constants.CLAIM_VECTORS),
                Arrays.asList(Constants.TITLE_VECTORS,Constants.CLASS_VECTORS,Constants.SUBCLASS_VECTORS), toSaveModel);
    }

    protected abstract MultiLayerNetwork buildAndFitModel();

    protected void saveModel(File toSave) throws IOException {
        ModelSerializer.writeModel(model, toSave, true);
    }
}
