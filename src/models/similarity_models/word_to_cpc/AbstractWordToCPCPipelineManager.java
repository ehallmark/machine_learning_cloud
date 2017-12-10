package models.similarity_models.word_to_cpc;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import seeding.Database;

import java.io.File;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public abstract class AbstractWordToCPCPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    @Getter
    private Map<String,Integer> wordToIdxMap;
    protected String modelName;
    private Map<String,INDArray> assetToEncodingMap;
    private DefaultPipelineManager<?,INDArray> previousManager;
    private final File currentVocabMapFile;
    private int batchSize;
    public AbstractWordToCPCPipelineManager(String modelName, File currentVocabMapFile, File inputData, File predictionsFile, int batchSize, DefaultPipelineManager<?,INDArray> previousManager) {
        super(inputData,predictionsFile);
        this.currentVocabMapFile=currentVocabMapFile;
        this.modelName=modelName;
        this.previousManager=previousManager;
        this.batchSize=batchSize;
    }

    @Override
    public void rebuildPrerequisiteData() {
        // update vocabulary
        System.out.println("Rebuilding vocabulary map...");
        final int totalDocCount = 5000000;
        LabelAwareIterator iterator = new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN);
        final int minDocCount = 10;
        final int maxDocCount = Math.round(0.2f*totalDocCount);
        WordToCPCIterator vocabIter = new WordToCPCIterator(iterator,batchSize);
        vocabIter.buildVocabMap(minDocCount,maxDocCount);
        wordToIdxMap = vocabIter.getWordToIdxMap();
        System.out.println("Vocab size: "+wordToIdxMap.size());
        saveVocabMap();
    }

    private void saveVocabMap() {
        Database.trySaveObject(wordToIdxMap,currentVocabMapFile);
    }

    public Map<String,Integer> loadVocabMap() {
        wordToIdxMap = (Map<String,Integer>)Database.tryLoadObject(currentVocabMapFile);
        return wordToIdxMap;
    }

    @Override
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(dataFolder);
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return batchSize;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    @Override
    protected void setDatasetManager() {
        datasetManager = new PreSaveDataSetManager(dataFolder,
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.DEV1)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TEST))
        );
    }

    protected Map<String,INDArray> getAssetToEncodingMap() {
        if(assetToEncodingMap==null) {
            assetToEncodingMap = previousManager.loadPredictions();
        }
        return assetToEncodingMap;
    }


    protected DataSetIterator getRawIterator(LabelAwareIterator iterator) {
        return new WordToCPCIterator(iterator,getAssetToEncodingMap(),getWordToIdxMap(),getBatchSize(),false,false,false);
    }


}
