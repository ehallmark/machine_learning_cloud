package models.similarity_models.deep_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Setter;
import models.similarity_models.signatures.CPCDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepCPCVAEPipelineManager extends DefaultPipelineManager<INDArray> {
    public static final String MODEL_NAME = "deep_cpc_autoencoder";
    public static final int MAX_CPC_DEPTH = 5;
    private static final int BATCH_SIZE = 128;
    private static final int MIN_CPC_APPEARANCES = 200;
    private static final File INPUT_DATA_FOLDER = new File("deep_cpc_vae_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"deep_cpc_vae_predictions/predictions_map.jobj");
    private Map<String,? extends Collection<CPC>> cpcMap;
    @Setter
    private CPCHierarchy hierarchy;
    private Map<String,Integer> cpcToIdxMap;
    private String modelName;
    public DeepCPCVAEPipelineManager(String modelName) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.modelName=modelName;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new DeepCPCVariationalAutoEncoderNN(this, modelName, MAX_CPC_DEPTH);
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
    protected void setDatasetManager() {
        datasetManager = new PreSaveDataSetManager(dataFolder,
                getRawIterator(trainAssets, false),
                getRawIterator(testAssets,true),
                getRawIterator(validationAssets, true)
        );
    }
    public int getMinCPCOccurrences() {
        return MIN_CPC_APPEARANCES;
    }

    @Override
    public void rebuildPrerequisiteData() {
        hierarchy = CPCHierarchy.updateAndGetLatest();
    }

    @Override
    public synchronized DataSetManager getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(dataFolder);
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }

    public synchronized CPCHierarchy getHierarchy() {
        if(hierarchy==null) {
            hierarchy = new CPCHierarchy();
            hierarchy.loadGraph();
        }
        return hierarchy;
    }

    public synchronized Map<String,? extends Collection<CPC>> getCPCMap() {
        if(cpcMap==null) {
            getHierarchy();
            getCpcToIdxMap();

            Set<String> allAssets = new HashSet<>(Database.getAllPatentsAndApplications());
            Map<String,Set<String>> assetToCPCStringMap = Collections.synchronizedMap(new HashMap<>(new AssetToCPCMap().getApplicationDataMap()));
            assetToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());

            cpcMap = assetToCPCStringMap.entrySet().parallelStream()
                    .filter(e->allAssets.contains(e.getKey()))
                    .collect(Collectors.toMap(e->e.getKey(), e ->
                                    e.getValue().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                                    .filter(cpc->cpc!=null)
                                    .flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream())
                                    .distinct()
                                    .filter(cpc -> cpc.getNumParts() <= MAX_CPC_DEPTH)
                                    .filter(cpc -> cpcToIdxMap.containsKey(cpc.getName()))
                                    .collect(Collectors.toSet())
                            )
                    )
                    .entrySet().parallelStream()
                    .filter(e->e.getValue().size()>0)
                    .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
        }
        return cpcMap;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        int limit = 1000000;
        int numTest = 20000;
        getCPCMap();
        System.out.println("Loaded cpcMap");
        List<String> allAssets = new ArrayList<>(cpcMap.keySet().parallelStream().filter(asset->cpcMap.containsKey(asset)).sorted().collect(Collectors.toList()));

        System.out.println("Splitting test and train");
        Random rand = new Random(69);
        Collections.shuffle(allAssets,rand);
        testAssets = new ArrayList<>();
        testAssets.addAll(allAssets.subList(0,numTest));
        validationAssets = new ArrayList<>();
        validationAssets.addAll(allAssets.subList(numTest,numTest*2));
        trainAssets = new ArrayList<>();
        trainAssets.addAll(allAssets.subList(numTest*2,Math.min(allAssets.size(),limit+numTest*2)));
        allAssets.clear();
        System.out.println("Num training: "+trainAssets.size());
        System.out.println("Num test: "+testAssets.size());
        System.out.println("Num validation: "+validationAssets.size());
    }


    protected DataSetIterator getRawIterator(List<String> assets, boolean test) {
        boolean shuffle = !test;
        return new CPCDataSetIterator(assets,shuffle,BATCH_SIZE,cpcMap,getCpcToIdxMap());
    }

    public Map<String,Integer> getCpcToIdxMap() {
        if(cpcToIdxMap==null) {
            RecursiveTask<CPCHierarchy> hierarchyTask = new RecursiveTask<CPCHierarchy>() {
                @Override
                protected CPCHierarchy compute() {
                    return getHierarchy();
                }
            };
            cpcToIdxMap = DeepCPCIndexMap.loadOrCreateMapForDepth(hierarchyTask,MAX_CPC_DEPTH,getMinCPCOccurrences());
        }
        return cpcToIdxMap;
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 5;
        String modelName = MODEL_NAME;

        setLoggingLevel(Level.INFO);
        DeepCPCVAEPipelineManager pipelineManager = new DeepCPCVAEPipelineManager(modelName);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

    public static void setLoggingLevel(Level level) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(level);
        } catch (Exception e) {
            System.out.println("Error setting log level: "+e.getMessage());
        }
    }
}
