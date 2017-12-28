package models.keyphrase_prediction;

import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.CombinedModel;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import models.NDArrayHelper;
import models.keyphrase_prediction.models.DefaultModel;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.combined_similarity_model.CombinedSimilarityModel;
import models.similarity_models.combined_similarity_model.CombinedSimilarityPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionPipelineManager extends DefaultPipelineManager<WordCPCIterator,List<String>> {
    public static final Model modelParams = new DefaultModel();
    private static final File keywordToVectorLookupTableFile = new File(Constants.DATA_FOLDER+"keyword_vector_lookup_table.jobj");
    private static Map<MultiStem,INDArray> keywordToVectorLookupTable;
    private static Map<String,INDArray> filingToVectorMap;
    private WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    private static final File INPUT_DATA_FOLDER = new File("keyphrase_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");

    private Set<MultiStem> multiStemSet;
    private static MultiLayerNetwork filingToEncodingNet;
    private static MultiLayerNetwork wordToEncodingNet;
    public KeyphrasePredictionPipelineManager(WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    @Override
    public DataSetManager<WordCPCIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    public Map<MultiStem,INDArray> getKeywordToVectorLookupTable() {
        if(keywordToVectorLookupTable==null) {
            keywordToVectorLookupTable = (Map<MultiStem,INDArray>) Database.tryLoadObject(keywordToVectorLookupTableFile);
        }
        return keywordToVectorLookupTable;
    }

    public Map<String,INDArray> getFilingToVectorMap() {
        if(filingToVectorMap==null) {
            buildFilingToVectorMap();
        }
        return filingToVectorMap;
    }

    @Override
    public void rebuildPrerequisiteData() {
        initStages(false,true);
    }

    private void initStages(boolean rerunVocab, boolean rerunFilters) {
        // stage 1;
        Stage1 stage1 = new Stage1(modelParams);
        stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // valid word
        System.out.println("Pre-grouping data for valid word stage...");
        ValidWordStage validWordStage = new ValidWordStage(stage2.get(), modelParams);
        validWordStage.run(rerunFilters);

        // cpc density
        System.out.println("Pre-grouping data for cpc density...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        CPCDensityStage cpcDensityStage = new CPCDensityStage(validWordStage.get(), modelParams, hierarchy);
        cpcDensityStage.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(cpcDensityStage.get(), modelParams);
        stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        multiStemSet = stage3.get();
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        System.out.println("Starting to init model...");
        if(multiStemSet==null) initStages(false,false);

        System.out.println("Loading keyword to vector table...");
        getKeywordToVectorLookupTable();
        if(keywordToVectorLookupTable==null) {
            buildKeywordToLookupTableMap();
        }
        System.out.println("Loading filing to vector table...");
        getFilingToVectorMap();

        System.out.println("Building keyword matrix...");
        List<MultiStem> keywords = Collections.synchronizedList(new ArrayList<>(keywordToVectorLookupTable.keySet()));
        INDArray keywordMatrix = Nd4j.create(keywordToVectorLookupTable.size(),keywordToVectorLookupTable.values().stream().findAny().get().length());

        for(int i = 0; i < keywords.size(); i++) {
            keywordMatrix.put(i,keywordToVectorLookupTable.get(keywords.get(i)));
        }

        keywordMatrix.diviColumnVector(keywordMatrix.norm2(1));

        if(filingToEncodingNet==null) loadSimilarityNetworks();

        System.out.println("Starting predictions...");
        getFilingToVectorMap().entrySet().forEach(e->{
            String filing = e.getKey();
            INDArray vec = e.getValue();
            INDArray encoding = filingToEncodingNet.activateSelectedLayers(0,filingToEncodingNet.getnLayers()-1,vec);
            // find most similar
            int best = Nd4j.argMax(encoding.divi(encoding.norm2Number()).broadcast(keywordMatrix.shape()).muli(keywordMatrix).sum(1),0).getInt(0);

            System.out.println("Best keyword for "+filing+": "+keywords.get(best));
        });
    }

    private void buildFilingToVectorMap() {
        String similarityModelName = CombinedSimilarityPipelineManager.MODEL_NAME;
        CombinedSimilarityPipelineManager combinedSimilarityPipelineManager = new CombinedSimilarityPipelineManager(similarityModelName,null,null,new CPCVAEPipelineManager(CPCVAEPipelineManager.MODEL_NAME));
        combinedSimilarityPipelineManager.initModel(false);

        filingToVectorMap = combinedSimilarityPipelineManager.getAssetToEncodingMap();
    }

    private void loadSimilarityNetworks() {
        String similarityModelName = CombinedSimilarityPipelineManager.MODEL_NAME;
        CombinedSimilarityPipelineManager combinedSimilarityPipelineManager = new CombinedSimilarityPipelineManager(similarityModelName,null,null,null);
        combinedSimilarityPipelineManager.initModel(false);

        CombinedModel combinedModel = (CombinedModel)combinedSimilarityPipelineManager.getModel().getNet();

        filingToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.CPC_VEC_NET);
        wordToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.WORD_CPC_2_VEC);

    }

    private void buildKeywordToLookupTableMap() {
        if(wordToEncodingNet==null) loadSimilarityNetworks();

        // get vectors
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);
        Word2Vec word2Vec = (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet();

        keywordToVectorLookupTable = Collections.synchronizedMap(new HashMap<>());

        multiStemSet.stream().forEach(stem->{
            INDArray vec = word2Vec.lookupTable().vector(stem.toString());
            if(vec!=null) {
                INDArray encoding = wordToEncodingNet.activateSelectedLayers(0,wordToEncodingNet.getnLayers()-1,vec);
                if(encoding!=null) {
                    keywordToVectorLookupTable.put(stem, encoding);
                }
            }
        });

        System.out.println("Stem lookup table size: "+keywordToVectorLookupTable.size());
        Database.saveObject(keywordToVectorLookupTable,keywordToVectorLookupTableFile);
    }

    @Override
    protected void splitData() {
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            datasetManager = wordCPC2VecPipelineManager.getDatasetManager();
        }
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        final int maxSamples = 200;
        final int windowSize = 8;
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = modelParams.getModelName();

        String CPC2VecModelName = WordCPC2VecPipelineManager.MODEL_NAME;

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(CPC2VecModelName,nEpochs,windowSize,maxSamples);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);


        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }
}
