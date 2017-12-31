package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public abstract class AbstractCombinedSimilarityPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "combined_similarity_model_small";
    private static final int BATCH_SIZE = 16;

    protected String modelName;
    protected Map<String,INDArray> assetToEncodingMap;
    protected WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    protected CPCVAEPipelineManager cpcvaePipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    public AbstractCombinedSimilarityPipelineManager(File inputDataFolder, File predictionsDataFile, String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, CPCVAEPipelineManager cpcvaePipelineManager) {
        super(inputDataFolder,predictionsDataFile);
        this.word2Vec=word2Vec;
        this.modelName=modelName;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
        this.cpcvaePipelineManager=cpcvaePipelineManager;
    }

    public abstract void initModel(boolean forceRecreateModels);

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

    public File getDevFile() {
        return FileTextDataSetIterator.devFile2;
    }

    @Override
    protected void setDatasetManager() {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
        File devFile = new File(baseDir, getDevFile().getName());

        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        System.out.println("Using full text: "+fullText);

        WordCPCIterator trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,20, fullText);
        WordCPCIterator testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,20, fullText);
        WordCPCIterator devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,20, fullText);

        trainIter.setRunVocab(false);
        testIter.setRunVocab(false);
        devIter.setRunVocab(false);

        long numDocs = Database.getAllPatentsAndApplications().size()*3;

        datasetManager = new NoSaveDataSetManager<>(
                getRawIterator(trainIter,numDocs,BATCH_SIZE),
                getRawIterator(testIter,numDocs, 128),
                getRawIterator(devIter,numDocs, 128)
        );
    }

    public synchronized Map<String,INDArray> getAssetToEncodingMap() {
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


    protected DataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, long numDocs, int batch) {
        return new Word2VecToCPCIterator(iterator,numDocs,getAssetToEncodingMap(),word2Vec,batch);
    }

}
