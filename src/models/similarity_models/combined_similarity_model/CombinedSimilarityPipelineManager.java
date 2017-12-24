package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.stages.Stage1;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CombinedSimilarityPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "combined_similarity_model";
    private static final int BATCH_SIZE = 16;
    private static final File INPUT_DATA_FOLDER = new File("combined_similarity_model_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"combined_similarity_model_predictions/predictions_map.jobj");
    private String modelName;
    private Map<String,INDArray> assetToEncodingMap;
    private WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    private CPCVAEPipelineManager cpcvaePipelineManager;
    @Getter
    private Word2Vec word2Vec;
    private int nEpochs;
    public CombinedSimilarityPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, CPCVAEPipelineManager cpcvaePipelineManager, int nEpochs) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.word2Vec=word2Vec;
        this.nEpochs=nEpochs;
        this.modelName=modelName;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
        this.cpcvaePipelineManager=cpcvaePipelineManager;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new CombinedSimilarityModel(this,modelName);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    public void rebuildPrerequisiteData() {

    }


    @Override
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            //datasetManager = new PreSaveDataSetManager(dataFolder);
            setDatasetManager();
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    @Override
    protected void setDatasetManager() {
        File trainFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.testFile.getName());
        File devFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.devFile2.getName());

        SequenceIterator<VocabWord> trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),nEpochs,wordCPC2VecPipelineManager.getCPCMap(),1,-1);
        SequenceIterator<VocabWord> testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),nEpochs,wordCPC2VecPipelineManager.getCPCMap(),1,-1);
        SequenceIterator<VocabWord> devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),nEpochs,wordCPC2VecPipelineManager.getCPCMap(),1,-1);

        datasetManager = new NoSaveDataSetManager<>(
                getRawIterator(trainIter),
                getRawIterator(testIter),
                getRawIterator(devIter)
        );
    }

    protected synchronized Map<String,INDArray> getAssetToEncodingMap() {
        if(assetToEncodingMap==null) {
            // convert to filing map
            assetToEncodingMap = Collections.synchronizedMap(new HashMap<>());
            Map<String,INDArray> cpcVaeEncodings = cpcvaePipelineManager.loadPredictions();
            AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
            assetToFilingMap.getApplicationDataMap().entrySet().parallelStream().forEach(e->{
                INDArray vec = cpcVaeEncodings.get(e.getKey());
                if(vec!=null) {
                    assetToEncodingMap.put(e.getValue(), vec);
                }
            });
            assetToFilingMap.getPatentDataMap().entrySet().parallelStream().forEach(e->{
                INDArray vec = cpcVaeEncodings.get(e.getKey());
                if(vec!=null) {
                    assetToEncodingMap.put(e.getValue(), vec);
                }
            });
        }
        return assetToEncodingMap;
    }


    protected DataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator) {
        return new Word2VecToCPCIterator(iterator,getAssetToEncodingMap(),word2Vec,BATCH_SIZE);
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;
        int windowSize = 4;
        int maxSamples = 300;

        int nEpochs = 5;
        String modelName = MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
        String wordCpc2VecModel = WordCPC2VecPipelineManager.MODEL_NAME;

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel,-1,windowSize,maxSamples);
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);

        setLoggingLevel(Level.INFO);
        CombinedSimilarityPipelineManager pipelineManager = new CombinedSimilarityPipelineManager(modelName, (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet(), wordCPC2VecPipelineManager, new CPCVAEPipelineManager(cpcEncodingModel), nEpochs);

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
