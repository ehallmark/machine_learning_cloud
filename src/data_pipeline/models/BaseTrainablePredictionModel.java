package data_pipeline.models;

import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class BaseTrainablePredictionModel<T,N> implements TrainablePredictionModel<T,N> {
    protected static Map<String,Map<LocalDateTime,Double>> modelToScoreMap; // Stores results for ALL models. Bad idea?
    protected static File modelToScoreMapFile = new File(Constants.DATA_FOLDER+"prediction_models_to_score_map.jobj");
    static {
        try {
            modelToScoreMap = loadModelScoreMap();
        } catch(Exception e) {

        }
        if(modelToScoreMap==null) {
            modelToScoreMap = Collections.synchronizedMap(new HashMap<>());
        }
    }
    protected AtomicBoolean isSaved;
    protected String modelName;
    @Getter
    protected N net;

    protected BaseTrainablePredictionModel(String modelName) {
        this.modelName = modelName;
        this.isSaved = new AtomicBoolean(false);
    }

    protected Map<Integer,Double> createSchedule(double learningRate, int nEpochs, int batchSize, int numExamples, int stepsPerEpoch) {
        // half learning rate each epoch policy
        Map<Integer,Double> schedule = new HashMap<>();
        int iterationsPerEpoch = numExamples/(stepsPerEpoch*batchSize);
        for(int i = 0; i < nEpochs*stepsPerEpoch; i++) {
            schedule.put(i*iterationsPerEpoch,learningRate/Math.pow(2,i));
        }
        return schedule;
    }

    public abstract File getModelBaseDirectory();

    protected File getModelFile(LocalDateTime dateTime) {
        return new File(getModelBaseDirectory(), modelName+"_"+dateTime.format(DATE_TIME_FORMAT));
    }

    private FileFilter defaultFileFilter = file -> {
        boolean filter = file!=null&&file.getName().startsWith(modelName+"_");
        if(filter) {
            try {
                LocalDateTime.parse(file.getName().substring(modelName.length()+1), DATE_TIME_FORMAT);
                return true;
            } catch(Exception e) {
                return false;
            }
        }
        return false;
    };

    public File getMostRecentModelFile() {
        File baseDir = getModelBaseDirectory();
        System.out.println("Looking in folder: "+baseDir.getAbsolutePath());
        if(baseDir==null||!baseDir.exists()||!baseDir.isDirectory()) return null;
        File[] matchingFiles = baseDir.listFiles(defaultFileFilter);
        System.out.println("Looking for file: "+modelName);
        if(matchingFiles==null||matchingFiles.length==0) return null;
        return Stream.of(matchingFiles).sorted((f1,f2)->{
            String s1 = f1.getName().substring(modelName.length()+1);
            String s2 = f2.getName().substring(modelName.length()+1);
            LocalDateTime d1 = LocalDateTime.parse(s1, DATE_TIME_FORMAT);
            LocalDateTime d2 = LocalDateTime.parse(s2, DATE_TIME_FORMAT);
            return d2.compareTo(d1);
        }).findFirst().orElse(null);
    }

    public File getModelFileWithoutDates() {
        return new File(getModelBaseDirectory(),modelName);
    }

    public File getBestModelFile() {
        mergeModelScoreMaps();
        File baseDir = getModelBaseDirectory();
        if(baseDir==null||!baseDir.exists()||!baseDir.isDirectory()) return null;
        File[] matchingFiles = baseDir.listFiles(defaultFileFilter);
        if(matchingFiles==null||matchingFiles.length==0) return null;
        Map<LocalDateTime,Double> scoreMap = modelToScoreMap.get(modelName);
        if(scoreMap==null) return null;
        File bestFile = Stream.of(matchingFiles).filter(f->{
            String s = f.getName().substring(modelName.length()+1);
            LocalDateTime d = LocalDateTime.parse(s, DATE_TIME_FORMAT);
            return scoreMap.containsKey(d);
        }).sorted((f1,f2)->{
            String s1 = f1.getName().substring(modelName.length()+1);
            String s2 = f2.getName().substring(modelName.length()+1);
            LocalDateTime d1 = LocalDateTime.parse(s1, DATE_TIME_FORMAT);
            LocalDateTime d2 = LocalDateTime.parse(s2, DATE_TIME_FORMAT);
            return scoreMap.get(d1).compareTo(scoreMap.get(d2));
        }).findFirst().orElse(null);
        if(bestFile!=null) {
            String s = bestFile.getName().substring(modelName.length() + 1);
            LocalDateTime d = LocalDateTime.parse(s, DATE_TIME_FORMAT);
            double score = scoreMap.get(d);
            System.out.println("Loading model from file "+bestFile.getAbsolutePath()+" with best score of "+score);
        }
        return bestFile;
    }

    @Override
    public synchronized void save(LocalDateTime time, double score) throws IOException {
        this.save(time,score,net);
    }

    @Override
    public synchronized void save(LocalDateTime time, double score, N net) throws IOException {
        if(net!=null) {
            isSaved.set(true);
            // merge models core map (in case it was updated by another process)
            mergeModelScoreMaps();
            modelToScoreMap.putIfAbsent(modelName, Collections.synchronizedMap(new HashMap<>()));
            modelToScoreMap.get(modelName).put(time,score);
            if(!getModelBaseDirectory().exists()) getModelBaseDirectory().mkdirs();
            File networkFile = getModelFile(time);
            saveNet(net,networkFile);
            saveModelToScoreMap();
        }
    }

    protected abstract void saveNet(N net, File file) throws IOException;

    public synchronized boolean isSaved() {
        return isSaved.get();
    }

    public synchronized void loadMostRecentModel() throws IOException {
        File modelFile = getMostRecentModelFile();
        if(modelFile==null) System.out.println("No most recent model found...");
        restoreFromFile(modelFile);
    }

    public synchronized void loadBestModel() throws IOException {
        File modelFile = getBestModelFile();
        if(modelFile==null) {
            System.out.println("No best model found... Reverting to most recent model.");
            modelFile = getMostRecentModelFile();
        }
        restoreFromFile(modelFile);
    }

    public synchronized void loadModelWithoutDates() throws Exception {
        File modelFile = getModelFileWithoutDates();
        if(modelFile==null) {
            System.out.println("No model without dates found... reverting to new model.");
        } else {
            restoreFromFile(modelFile);
        }
    }
    protected abstract void restoreFromFile(File modelFile) throws IOException;

    private static Map<String,Map<LocalDateTime,Double>> loadModelScoreMap() {
        return (Map<String,Map<LocalDateTime,Double>>)Database.tryLoadObject(modelToScoreMapFile);
    }

    private static void saveModelToScoreMap() {
        Database.trySaveObject(modelToScoreMap, modelToScoreMapFile);
    }

    private static void mergeModelScoreMaps() {
        try {
            Map<String,Map<LocalDateTime,Double>> tmp = loadModelScoreMap();
            if(tmp!=null) {
                modelToScoreMap.putAll(tmp);
            }
        } catch(Exception e) {
        }
    }
}
