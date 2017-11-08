package data_pipeline.pipeline_manager;

import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.vectorize.DatasetManager;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import org.deeplearning4j.nn.api.Model;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import seeding.Database;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class DefaultPipelineManager<T> implements PipelineManager<T> {
    protected List<String> testAssets;
    protected List<String> trainAssets;
    protected List<String> validationAssets;
    protected DatasetManager datasetManager;
    protected File dataFolder;
    protected File predictionsFile;
    protected TrainablePredictionModel<T> model;

    protected DefaultPipelineManager(File dataFolder, File finalPredictionsFile) {
        this.dataFolder=dataFolder;
        this.predictionsFile=finalPredictionsFile;
    }

    // init the trainable prediction model
    protected abstract void initModel(boolean forceRecreateModel);

    // split data into test/train/dev
    protected abstract void splitData();

    // return iterator
    protected abstract DataSetIterator getRawIterator(List<String> assets, boolean test);

    @Override
    public void saveRawDatasets() {
        splitData();
        System.out.println("Finished splitting test and train.");
        System.out.println("Num training: "+trainAssets.size());
        System.out.println("Num test: "+testAssets.size());
        System.out.println("Num validation: "+validationAssets.size());

        if(!dataFolder.exists()) dataFolder.mkdir();
        datasetManager = new DatasetManager(dataFolder,
                getRawIterator(trainAssets, false),
                getRawIterator(testAssets,true),
                getRawIterator(validationAssets, true)
        );
        datasetManager.removeDataFromDisk();
        System.out.println("Saving datasets...");
        datasetManager.saveDataSets();
    }

    @Override
    public void trainModels(int nEpochs) {
        model.train(nEpochs);
        if(!model.isSaved()) {
            try {
                model.save();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Map<String,T> predict(List<String> items) {
        return model.predict(items);
    }

    @Override
    public void savePredictions(Map<String,T> predictions) {
        System.out.println("Saving predictions...");
        Database.trySaveObject(predictions,predictionsFile);
        System.out.println("Saved.");
    }

    @Override
    public void runPipeline(boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets) {
        // start with data pipeline
        rebuildDatasets = rebuildDatasets || ! dataFolder.exists();

        // STAGE 1 of pipeline: LOAD DATA
        if(rebuildDatasets) {
            System.out.println("Building datasets...");
            saveRawDatasets();
        }

        // STAGE 2 of pipeline: TRAINING MODELS
        if(runModels) {
            if(model==null) initModel(forceRecreateModel);
            System.out.println("Training models...");
            trainModels(nTrainingEpochs);
        }

        if(predictAssets) {
            if(model==null) initModel(forceRecreateModel);
            System.out.println("Predicting results...");
            Map<String,T> allPredictions = predict(new ArrayList<>(Database.getAllPatentsAndApplications()));
            savePredictions(allPredictions);
        }
    }
}
