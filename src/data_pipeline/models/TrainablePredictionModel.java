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
public interface TrainablePredictionModel<T,N> {
    DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    Map<String,T> predict(List<String> assets, List<String> assignees);
    void train(int nEpochs);
    File getModelBaseDirectory();

    File getMostRecentModelFile();

    File getBestModelFile();

    N getNet();

    void save(LocalDateTime time, double score) throws IOException;

    void save(LocalDateTime time, double score, N net) throws IOException;

    boolean isSaved();

    void loadMostRecentModel() throws IOException;

    void loadBestModel() throws IOException;


}
