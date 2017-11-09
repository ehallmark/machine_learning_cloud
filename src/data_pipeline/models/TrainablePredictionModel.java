package data_pipeline.models;

import lombok.Getter;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class TrainablePredictionModel<T> {
    protected static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;
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

    public synchronized void save(LocalDateTime time) throws IOException {
        if(net!=null) {
            isSaved.set(true);
            if(!getModelBaseDirectory().exists()) getModelBaseDirectory().mkdirs();
            File networkFile = getModelFile(time);
            ModelSerializer.writeModel(net,networkFile,true);
        }
    }

    public synchronized boolean isSaved() {
        return isSaved.get();
    }

    public synchronized void loadMostRecentModel() throws IOException {
        File modelFile = getMostRecentModelFile();
        if(modelFile!=null&&modelFile.exists()) {
            this.net = ModelSerializer.restoreMultiLayerNetwork(modelFile, true);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }
}
