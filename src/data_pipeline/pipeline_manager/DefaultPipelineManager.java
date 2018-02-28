package data_pipeline.pipeline_manager;

import ch.qos.logback.classic.Level;
import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.vectorize.DataSetManager;
import lombok.Getter;
import lombok.Setter;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    @Setter
    private Map<String,T> predictions;

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

            datasetManager.removeDataFromDisk();
            System.out.println("Saving datasets...");
            datasetManager.saveDataSets();
        } else {
            System.out.println("Warning: No dataset manager...");
        }
    }

    public static void setCudaEnvironment() {
        // setup cuda env
        try {
            Nd4j.getMemoryManager().setAutoGcWindow(300);
            CudaEnvironment.getInstance().getConfiguration().setMaximumGridSize(512).setMaximumBlockSize(512)
                    .setMaximumDeviceCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumDeviceCache(10L * 1024 * 1024 * 1024L)
                    .setMaximumHostCacheableLength(2L * 1024 * 1024 * 1024L)
                    .setMaximumHostCache(10L * 1024 * 1024 * 1024L);
        } catch(Exception e) {
            e.printStackTrace();
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
    public Map<String,T> updatePredictions(List<String> items, List<String> assignees, List<String> classCodes) {
        Map<String,T> previous = loadPredictions();
        if(previous!=null) {
            items = items.stream().filter(item -> !previous.containsKey(item)).collect(Collectors.toList());
            assignees = assignees.stream().filter(assignee -> !previous.containsKey(assignee)).collect(Collectors.toList());
            classCodes = classCodes.stream().filter(classCode -> !previous.containsKey(classCode)).collect(Collectors.toList());
        }
        Map<String,T> newPredictions = model.predict(items,assignees,classCodes);
        if(previous!=null) {
            if(newPredictions!=null) {
                previous.putAll(newPredictions);
            }
            return previous;
        } else {
            return newPredictions;
        }
    }

    @Override
    public void savePredictions(Map<String,T> predictions) {
        this.predictions=predictions;
        System.out.println("Saving predictions for "+this.getClass().getSimpleName()+"...");
        File predictionsDir = predictionsFile.getParentFile();
        if(!predictionsDir.exists()) predictionsDir.mkdirs();
        Database.trySaveObject(predictions,predictionsFile);
        System.out.println("Saved.");
    }

    @Override
    public synchronized Map<String,T> loadPredictions() {
        if(predictions==null) {
            System.out.println("Loading predictions for "+this.getClass().getSimpleName()+"...");
            predictions = (Map<String, T>) Database.tryLoadObject(predictionsFile);
            if(predictions==null) {
                predictions = Collections.synchronizedMap(new HashMap<>());
            }
        }
        return predictions;
    }

    public void runPipeline(boolean rebuildPrerequisites, boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets) {
        this.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModel,nTrainingEpochs,predictAssets,true);
    }

    @Override
    public void runPipeline(boolean rebuildPrerequisites, boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets, boolean forceRerunPredictions) {
        // start with data pipeline

        // STAGE 0 of pipeline: PREREQUISITES
        if(rebuildPrerequisites) {
            rebuildPrerequisiteData();
        }

        rebuildDatasets = rebuildDatasets || (runModels && ! dataFolder.exists());

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
            Map<String,T> allPredictions = forceRerunPredictions ? predict(allAssets, allAssignees, allClassCodes) : updatePredictions(allAssets, allAssignees, allClassCodes);
            if(allPredictions!=null) savePredictions(allPredictions);
        }

        if(model==null) initModel(forceRecreateModel);
    }

    public static void setLoggingLevel(Level level) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(level);
        } catch (Exception e) {
            System.out.println("Error setting log level: "+e.getMessage());
        }
    }
}
