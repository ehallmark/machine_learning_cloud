package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.models.TimeDensityModel;
import models.keyphrase_prediction.stages.Stage1;
import models.keyphrase_prediction.stages.ValidWordStage;
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
    private Set<String> onlyWords;
    private int maxSamples;
    public WordCPC2VecPipelineManager(String modelName, int numEpochs, Set<String> onlyWords, int maxSamples) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.modelName=modelName;
        this.onlyWords=onlyWords;
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
            File trainFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.testFile.getName());
            File devFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.devFile2.getName());

            FileTextDataSetIterator trainIter = new FileTextDataSetIterator(trainFile);
            FileTextDataSetIterator testIter = new FileTextDataSetIterator(testFile);
            FileTextDataSetIterator devIter = new FileTextDataSetIterator(devFile);

            datasetManager = new NoSaveDataSetManager<>(
                    new WordCPCIterator(trainIter,numEpochs,getCPCMap(), onlyWords, maxSamples),
                    new WordCPCIterator(testIter,1,getCPCMap(), onlyWords, maxSamples),
                    new WordCPCIterator(devIter,1,getCPCMap(), onlyWords, maxSamples)
            );
        }
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        final int maxSamples = 100;
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 10;
        String modelName = MODEL_NAME;

        ValidWordStage stage5 = new ValidWordStage(null,new TimeDensityModel());
        stage5.run(false);

        Set<String> onlyWords = stage5.get().stream().map(stem->stem.toString()).collect(Collectors.toSet());

        WordCPC2VecPipelineManager pipelineManager = new WordCPC2VecPipelineManager(modelName,nEpochs,onlyWords,maxSamples);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
