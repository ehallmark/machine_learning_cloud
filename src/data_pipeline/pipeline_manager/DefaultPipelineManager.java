package data_pipeline.pipeline_manager;

import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import seeding.Database;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class DefaultPipelineManager<D,T> implements PipelineManager<D,T> {
    protected List<String> testAssets;
    protected List<String> trainAssets;
    protected List<String> validationAssets;
    protected DataSetManager<D> datasetManager;
    protected File dataFolder;
    @Getter
    protected File predictionsFile;
    @Getter
    protected TrainablePredictionModel<T,?> model;

    protected DefaultPipelineManager(File dataFolder, File finalPredictionsFile) {
        this.dataFolder=dataFolder;
        this.predictionsFile=finalPredictionsFile;
    }

    // init the trainable prediction model
    protected abstract void initModel(boolean forceRecreateModel);

    // split data into test/train/dev
    protected abstract void splitData();

    @Override
    public void saveRawDatasets() {
        splitData();
        System.out.println("Finished splitting test and train.");

        setDatasetManager();
        if(datasetManager!=null) {
            if (!dataFolder.exists()) dataFolder.mkdir();

            setDatasetManager();

            datasetManager.removeDataFromDisk();
            System.out.println("Saving datasets...");
            datasetManager.saveDataSets();
        } else {
            System.out.println("Warning: No dataset manager...");
        }
    }

    protected abstract void setDatasetManager();

    @Override
    public void trainModels(int nEpochs) {
        model.train(nEpochs);
        if(!model.isSaved()) {
            try {
                model.save(LocalDateTime.now(),Double.MAX_VALUE);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Map<String,T> predict(List<String> items, List<String> assignees, List<String> classCodes) {
        return model.predict(items,assignees,classCodes);
    }

    @Override
    public void savePredictions(Map<String,T> predictions) {
        System.out.println("Saving predictions...");
        File predictionsDir = predictionsFile.getParentFile();
        if(!predictionsDir.exists()) predictionsDir.mkdirs();
        Database.trySaveObject(predictions,predictionsFile);
        System.out.println("Saved.");
    }

    @Override
    public Map<String,T> loadPredictions() {
        return (Map<String,T>)Database.tryLoadObject(predictionsFile);
    }

    @Override
    public void runPipeline(boolean rebuildPrerequisites, boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets) {
        // start with data pipeline

        // STAGE 0 of pipeline: PREREQUISITES
        if(rebuildPrerequisites) {
            rebuildPrerequisiteData();
        }

        rebuildDatasets = rebuildDatasets || ! dataFolder.exists();

        // STAGE 1 of pipeline: LOAD DATA
        if(rebuildDatasets) {
            if(model==null) initModel(forceRecreateModel);
            System.out.println("Building datasets...");
            saveRawDatasets();
        }

        // STAGE 2 of pipeline: TRAINING MODELS
        if(runModels) {
            if(model==null) initModel(forceRecreateModel);
            System.out.println("Training models...");
            trainModels(nTrainingEpochs);
        }

        // STAGE 3 of pipeline: PREDICTIONS
        if(predictAssets) {
            if(model==null) initModel(forceRecreateModel);
            System.out.println("Predicting results...");
            List<String> allAssets = new ArrayList<>(Database.getAllPatentsAndApplications());
            List<String> allAssignees = new ArrayList<>(Database.getAssignees());
            List<String> allClassCodes = new ArrayList<>(Database.getClassCodes());
            Map<String,T> allPredictions = predict(allAssets, allAssignees, allClassCodes);
            savePredictions(allPredictions);
        }

        if(model==null) initModel(forceRecreateModel);
    }
}
