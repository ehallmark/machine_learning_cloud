package data_pipeline.models;

import lombok.Getter;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class TrainablePredictionModel<T> {
    protected static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;
    protected static Map<String,Map<LocalDateTime,Double>> modelToScoreMap;
    protected static File modelToScoreMapFile = new File(Constants.DATA_FOLDER+"prediction_models_to_score_map.jobj");
    static {
        try {
            modelToScoreMap = (Map<String,Map<LocalDateTime,Double>>)Database.tryLoadObject(modelToScoreMapFile);
        } catch(Exception e) {

        }
        if(modelToScoreMap==null) {
            modelToScoreMap = Collections.synchronizedMap(new HashMap<>());
        }
    }
    protected AtomicBoolean isSaved;
    protected String modelName;
    @Getter
    protected MultiLayerNetwork net;

    protected TrainablePredictionModel(String modelName) {
        this.modelName = modelName;
        this.isSaved = new AtomicBoolean(false);
    }

    public abstract Map<String,T> predict(List<String> assets);

    public abstract void train(int nEpochs);

    public abstract File getModelBaseDirectory();

    protected File getModelFile(LocalDateTime dateTime) {
        return new File(getModelBaseDirectory(), modelName+"_"+dateTime.format(DATE_TIME_FORMAT));
    }

    protected File getMostRecentModelFile() {
        File baseDir = getModelBaseDirectory();
        if(baseDir==null||!baseDir.exists()||!baseDir.isDirectory()) return null;
        File[] matchingFiles = baseDir.listFiles(file->file!=null&&file.getName().startsWith(modelName+"_"));
        if(matchingFiles==null||matchingFiles.length==0) return null;
        return Stream.of(matchingFiles).sorted((f1,f2)->{
            String s1 = f1.getName().substring(modelName.length()+1);
            String s2 = f2.getName().substring(modelName.length()+1);
            LocalDateTime d1 = LocalDateTime.parse(s1, DATE_TIME_FORMAT);
            LocalDateTime d2 = LocalDateTime.parse(s2, DATE_TIME_FORMAT);
            return d2.compareTo(d1);
        }).findFirst().orElse(null);
    }

    protected File getBestModelFile() {
        File baseDir = getModelBaseDirectory();
        if(baseDir==null||!baseDir.exists()||!baseDir.isDirectory()) return null;
        File[] matchingFiles = baseDir.listFiles(file->file!=null&&file.getName().startsWith(modelName+"_"));
        if(matchingFiles==null||matchingFiles.length==0) return null;
        Map<LocalDateTime,Double> scoreMap = modelToScoreMap.get(modelName);
        if(scoreMap==null) return null;
        return Stream.of(matchingFiles).filter(f->{
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
    }

    public synchronized void save(LocalDateTime time, double score) throws IOException {
        if(net!=null) {
            isSaved.set(true);
            modelToScoreMap.putIfAbsent(modelName, Collections.synchronizedMap(new HashMap<>()));
            modelToScoreMap.get(modelName).put(time,score);
            if(!getModelBaseDirectory().exists()) getModelBaseDirectory().mkdirs();
            File networkFile = getModelFile(time);
            ModelSerializer.writeModel(net,networkFile,true);
            saveModelToScoreMap();
        }
    }

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

    private void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = ModelSerializer.restoreMultiLayerNetwork(modelFile, true);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

    private static void saveModelToScoreMap() {
        Database.trySaveObject(modelToScoreMap, modelToScoreMapFile);
    }
}
