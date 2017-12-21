package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.stages.Stage;
import models.keyphrase_prediction.stages.Stage1;
import models.text_streaming.ESTextDataSetIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPC2VecPipelineManager extends DefaultPipelineManager<WordCPCIterator,INDArray> {
    public static final String SMALL_MODEL_NAME = "wordcpc2vec_model";
    public static final String LARGE_MODEL_NAME = "wordcpc2vec_model_large";
    public static final int SMALL_VECTOR_SIZE = 32;
    public static final int LARGE_VECTOR_SIZE = 128;
    public static final Map<String,Integer> modelNameToVectorSizeMap = Collections.synchronizedMap(new HashMap<>());
    static {
        modelNameToVectorSizeMap.put(SMALL_MODEL_NAME,SMALL_VECTOR_SIZE);
        modelNameToVectorSizeMap.put(LARGE_MODEL_NAME,LARGE_VECTOR_SIZE);
    }
    private static final File INPUT_DATA_FOLDER = new File("wordcpc2vec_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"wordcpc2vec_predictions/predictions_map.jobj");
    protected Map<String,Collection<CPC>> cpcMap;
    private CPCHierarchy hierarchy;
    private String modelName;
    @Getter
    private int numEpochs;
    @Getter
    private List<String> testWords;
    private int maxSamples;
    public WordCPC2VecPipelineManager(String modelName, int numEpochs, int maxSamples) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.modelName=modelName;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00","semiconductor","computer","internet","virtual","intelligence","artificial","chemistry","biology","electricity","agriculture","automobile","robot");
    }

    @Override
    public void rebuildPrerequisiteData() {
        try {
            System.out.println("Starting to pull latest text data from elasticsearch...");
            ESTextDataSetIterator.main(null);
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WordCPC2VecModel(this, modelName, modelNameToVectorSizeMap.get(modelName));
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadMostRecentModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    public synchronized CPCHierarchy getHierarchy() {
        if(hierarchy==null) {
            hierarchy = new CPCHierarchy();
            hierarchy.loadGraph();
        }
        return hierarchy;
    }

    public synchronized Map<String,Collection<CPC>> getCPCMap() {
        if(cpcMap==null) {
            Map<String,String> patentToFiling = new AssetToFilingMap().getPatentDataMap();
            Map<String,String> appToFiling = new AssetToFilingMap().getApplicationDataMap();
            getHierarchy();
            Map<String,Set<String>> assetToCPCStringMap = new HashMap<>();
            new AssetToCPCMap().getApplicationDataMap().entrySet().forEach(e->{
                assetToCPCStringMap.put(appToFiling.get(e.getKey()),e.getValue());
            });
            new AssetToCPCMap().getPatentDataMap().entrySet().forEach(e->{
                assetToCPCStringMap.put(patentToFiling.get(e.getKey()),e.getValue());
            });
            cpcMap = assetToCPCStringMap.entrySet().parallelStream()
                    .filter(e->assetToCPCStringMap.containsKey(e.getKey()))
                    .collect(Collectors.toMap(e->e.getKey(), e->e.getValue().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                            .filter(cpc->cpc!=null)
                            .flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream())
                            .distinct()
                            .collect(Collectors.toSet())))
                    .entrySet().parallelStream()
                    .filter(e->e.getValue().size()>0)
                    .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
        }
        return cpcMap;
    }

    @Override
    protected void splitData() {
        // purposefully do nothing
    }


    @Override
    public DataSetManager<WordCPCIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            File trainFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.testFile.getName());
            File devFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.devFile2.getName());

            FileTextDataSetIterator trainIter = new FileTextDataSetIterator(trainFile);
            FileTextDataSetIterator testIter = new FileTextDataSetIterator(testFile);
            FileTextDataSetIterator devIter = new FileTextDataSetIterator(devFile);

            datasetManager = new NoSaveDataSetManager<>(
                    new WordCPCIterator(trainIter,numEpochs,getCPCMap(), maxSamples),
                    new WordCPCIterator(testIter,1,getCPCMap(), maxSamples),
                    new WordCPCIterator(devIter,1,getCPCMap(), maxSamples)
            );
        }
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        final int maxSamples = 200;
        boolean rebuildPrerequisites = true;
        boolean rebuildDatasets = true;
        boolean runModels = true;
        boolean forceRecreateModels = true;
        boolean runPredictions = false;

        rebuildPrerequisites = rebuildPrerequisites || !Stage.getTransformedDataFolder().exists();

        int nEpochs = 10;
        String modelName = LARGE_MODEL_NAME;
        WordCPC2VecPipelineManager pipelineManager = new WordCPC2VecPipelineManager(modelName,nEpochs,maxSamples);
        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
