package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPC2VecPipelineManager extends DefaultPipelineManager<WordCPCIterator,INDArray> {
    public static final String MODEL_NAME = "wordcpc2vec_model";
    private static final File INPUT_DATA_FOLDER = new File("wordcpc2vec_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"wordcpc2vec_predictions/predictions_map.jobj");

    protected Map<String,Collection<CPC>> cpcMap;
    private CPCHierarchy hierarchy;
    private String modelName;
    @Getter
    private int numEpochs;
    @Getter
    private List<String> testWords;
    public WordCPC2VecPipelineManager(String modelName, int numEpochs) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.modelName=modelName;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00","semiconductor","computer","internet","virtual","intelligence","artificial","chemistry","biology","electricity","agriculture","automobile","robot");
    }

    @Override
    public void rebuildPrerequisiteData() {

    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WordCPC2VecModel(this, modelName);
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
            Set<String> allAssets = new HashSet<>(Database.getAllPatentsAndApplications());
            getHierarchy();
            Map<String,Set<String>> patentToCPCStringMap = new HashMap<>();
            patentToCPCStringMap.putAll(new AssetToCPCMap().getApplicationDataMap());
            patentToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());
            cpcMap = patentToCPCStringMap.entrySet().parallelStream()
                    .filter(e->allAssets.contains(e.getKey()))
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
            datasetManager = new NoSaveDataSetManager<>(
                    new WordCPCIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN),numEpochs,getCPCMap()),
                    new WordCPCIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TEST),1,getCPCMap()),
                    new WordCPCIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.DEV1),1,getCPCMap())
            );
        }
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 10;
        String modelName = MODEL_NAME;

        WordCPC2VecPipelineManager pipelineManager = new WordCPC2VecPipelineManager(modelName,nEpochs);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
