package models.similarity_models.rnn_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class RNNTextEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    public static final int BATCH_SIZE = 1024;
    public static final int MAX_SEQUENCE_LENGTH = 128;
    public static final int MINI_BATCH_SIZE = 32;
    public static final String MODEL_NAME256 = "rnn_text_encoding_model256";
    public static final int VECTOR_SIZE = 32;
    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"rnn_text_encoding_model256_prediction/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("rnn_text_encoding_model256_input_data/");


    private String modelName;
    private int encodingSize;
    private Word2Vec word2Vec;
    private RNNTextEncodingPipelineManager(String modelName, Word2Vec word2Vec, int encodingSize) {
        super(INPUT_DATA_FOLDER_ALL, PREDICTION_FILE);
        this.word2Vec=word2Vec;
        this.modelName=modelName;
        this.encodingSize=encodingSize;
    }

    @Override
    protected void initModel(boolean forceRecreateModels) {
        model = new RNNTextEncodingModel(this,modelName,word2Vec.getLayerSize(),encodingSize);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadMostRecentModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    protected void splitData() {
        // do nothing
    }

    private MultiDataSetIterator getIterator(File[] files, int limit) {
        return new RNNEncodingIterator(word2Vec,new ZippedFileSequenceIterator(files,limit),BATCH_SIZE,MAX_SEQUENCE_LENGTH);
    }

    @Override
    protected void setDatasetManager() {
        if (datasetManager == null) {
            int trainSize = 100000000;
            int testSize = 50000;

            File dir = new File("/home/ehallmark/repos/poi/word2vec_text/");
            File[] allFiles = Stream.of(dir.listFiles()).sorted(Comparator.comparing(f->f.getName())).toArray(size->new File[size]);
            Random rand = new Random(2);
            File testFile = allFiles[allFiles.length-rand.nextInt(200)];
            File devFile = allFiles[allFiles.length-200-rand.nextInt(200)];

            File[] trainFiles = Stream.of(allFiles).filter(f->!f.getName().equals(testFile.getName())&&!f.getName().equals(devFile.getName()))
                    .toArray(size->new File[size]);

            MultiDataSetIterator trainIter = getIterator(trainFiles,trainSize);
            MultiDataSetIterator testIter = getIterator(new File[]{testFile},testSize);
            MultiDataSetIterator devIter = getIterator(new File[]{devFile},testSize);

            datasetManager = new PreSaveDataSetManager<>(
                    dataFolder,
                    trainIter,
                    testIter,
                    devIter,
                    true
            );
        }
    }

    @Override
    public DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager<>(dataFolder, MINI_BATCH_SIZE, true);
        }
        return datasetManager;
    }

    @Override
    public void rebuildPrerequisiteData() {

    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 5;
        String modelName = MODEL_NAME256;

        int encodingSize = 128;
        String word2VecPath = "giant_wordvectors256";
        Word2Vec word2Vec = WordVectorSerializer.readWord2VecModel(word2VecPath);

        setCudaEnvironment();
        setLoggingLevel(Level.INFO);


        RNNTextEncodingPipelineManager pipelineManager = new RNNTextEncodingPipelineManager(modelName,word2Vec,encodingSize);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
