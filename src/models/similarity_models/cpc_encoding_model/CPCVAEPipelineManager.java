package models.similarity_models.cpc_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.pipeline_manager.PipelineManager;
import data_pipeline.vectorize.DatasetManager;
import lombok.Setter;
import org.deeplearning4j.datasets.iterator.AsyncDataSetIterator;
import models.similarity_models.signatures.CPCDataSetIterator;
import org.deeplearning4j.optimize.solvers.BackTrackLineSearch;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CPCVAEPipelineManager extends DefaultPipelineManager<INDArray> {
    public static final int MAX_CPC_DEPTH = 4;
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("cpc_vae_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+INPUT_DATA_FOLDER.getName()+"_predictions_map.jobj");
    private Map<String,? extends Collection<CPC>> cpcMap;
    @Setter
    private CPCHierarchy hierarchy;
    private Map<String,Integer> cpcToIdxMap;
    private String modelName;
    public CPCVAEPipelineManager(String modelName) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.modelName=modelName;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new CPCVariationalAutoEncoderNN(this, modelName, MAX_CPC_DEPTH);
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
    public void rebuildPrerequisiteData() {
        hierarchy = CPCHierarchy.updateAndGetLatest();
    }

    @Override
    public synchronized DatasetManager getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new DatasetManager(dataFolder);
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
                            .filter(cpc -> cpc.getNumParts() <= MAX_CPC_DEPTH)
                            .filter(cpc -> cpcToIdxMap==null||cpcToIdxMap.containsKey(cpc.getName()))
                            .collect(Collectors.toSet())))
                    .entrySet().parallelStream()
                    .filter(e->e.getValue().size()>0)
                    .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
        }
        return cpcMap;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        int limit = 5000000;

        cpcToIdxMap = CPCIndexMap.loadOrCreateMapForDepth(getHierarchy(),MAX_CPC_DEPTH);

        getCPCMap();
        System.out.println("Loaded cpcMap");
        List<String> allAssets = new ArrayList<>(cpcMap.keySet().parallelStream().filter(asset->cpcMap.containsKey(asset)).sorted().collect(Collectors.toList()));

        System.out.println("Splitting test and train");
        Random rand = new Random(69);
        Collections.shuffle(allAssets,rand);
        testAssets = new ArrayList<>();
        testAssets.addAll(allAssets.subList(0,25000));
        validationAssets = new ArrayList<>();
        validationAssets.addAll(allAssets.subList(25000,50000));
        trainAssets = new ArrayList<>();
        trainAssets.addAll(allAssets.subList(50000,Math.min(allAssets.size(),limit+50000)));
        allAssets.clear();
    }


    @Override
    protected DataSetIterator getRawIterator(List<String> assets, boolean test) {
        boolean shuffle = !test;
        return new CPCDataSetIterator(assets,shuffle,BATCH_SIZE,cpcMap,cpcToIdxMap);
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = true;
        boolean runModels = true;
        boolean forceRecreateModels = true; // TODO change this once the hyperparameters are good
        boolean runPredictions = true;
        int nEpochs = 2;
        String modelName = "cpc_autoencoder";

        setLoggingLevel(Level.INFO);
        CPCVAEPipelineManager pipelineManager = new CPCVAEPipelineManager(modelName);
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
