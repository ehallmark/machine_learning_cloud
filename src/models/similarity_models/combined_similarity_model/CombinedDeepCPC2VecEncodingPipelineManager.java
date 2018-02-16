package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import data_pipeline.vectorize.CombinedFileMultiMinibatchIterator;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CombinedDeepCPC2VecEncodingPipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "combined_deep_cpc_rnn_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("combined_deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("combined_deep_cpc_all3_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 32;
    private static final int MAX_NETWORK_RECURSION = -1;
    private static int MAX_SAMPLE = 6;
    protected static final Random rand = new Random(235);
    private static CombinedDeepCPC2VecEncodingPipelineManager MANAGER;
    DeepCPCVAEPipelineManager deepCPCVAEPipelineManager;
    public CombinedDeepCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, DeepCPCVAEPipelineManager deepCPCVAEPipelineManager) {
        super(new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,MAX_SAMPLE,wordCPC2VecPipelineManager);
        this.deepCPCVAEPipelineManager=deepCPCVAEPipelineManager;
    }

    public static String currentDataFolderName(int recursion,int sample) {
        return (INPUT_DATA_FOLDER_ALL).getAbsolutePath()+sample+(recursion>0?("_r"+recursion):"");
    }

    public static String currentDataFolderName(int sample) {
        return currentDataFolderName(-1,sample);
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new CombinedDeepCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
        }
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
    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                dataSet.setLabels(null);
                dataSet.setLabelsMaskArray(null);
            }
        };
    }

    @Override
    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                dataSet.setLabels(dataSet.getFeatures());
                dataSet.setLabelsMaskArray(null);
                dataSet.setFeaturesMaskArrays(null);
            }
        };
    }

    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static synchronized CombinedDeepCPC2VecEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            DeepCPCVAEPipelineManager deepCPCVAEPipelineManager = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);
            if(loadWord2Vec) deepCPCVAEPipelineManager.runPipeline(false,false,false,false,-1,false);

            setLoggingLevel(Level.INFO);
            MANAGER = new CombinedDeepCPC2VecEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager, deepCPCVAEPipelineManager);
        }
        return MANAGER;
    }


    protected MultiDataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, int batch) {
        return new CombinedWord2VecToCPCIterator(iterator,word2Vec,(DeepCPCVariationalAutoEncoderNN)deepCPCVAEPipelineManager.getModel(),batch,getMaxSamples());
    }

    @Override
    protected void setDatasetManager() {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
        File devFile = new File(baseDir, getDevFile().getName());

        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        System.out.println("Using full text: "+fullText);

        WordCPCIterator trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);
        WordCPCIterator testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);
        WordCPCIterator devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);

        trainIter.setRunVocab(false);
        testIter.setRunVocab(false);
        devIter.setRunVocab(false);

        MultiDataSetIterator train =  getRawIterator(trainIter,getBatchSize());
        MultiDataSetIterator test =  getRawIterator(testIter,1024);
        MultiDataSetIterator val =  getRawIterator(devIter,1024);

        PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                // need the dataset file with 1 extra sample
                dataFolder,
                train,
                test,
                val,
                true
        );
        manager.setMultiDataSetPreProcessor(getSeedTimeMultiDataSetPreProcessor());

        datasetManager = manager;
    }

    private INDArray[] buildVectors(int[] indices, List<List<String>> _entries) {
        List<List<String>> entries = IntStream.of(indices).mapToObj(i->_entries.get(i)).collect(Collectors.toList());
        return buildVectors(entries,word2Vec,getMaxSamples());
    }

    public static INDArray[] buildVectors(List<List<String>> entries, Word2Vec word2Vec, int sample) {
        INDArray[] vectors = entries.stream().map(cpcLabels->{
            int numCPCLabels = cpcLabels.size();
            if(sample>numCPCLabels) {
                return null;
            } else {
                return word2Vec.getWordVectors(cpcLabels).transpose();
            }
        }).filter(vec->vec!=null).toArray(size->new INDArray[size]);
        return vectors;
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 1;

        rebuildDatasets = runModels && !new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)).exists();

        CombinedDeepCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
