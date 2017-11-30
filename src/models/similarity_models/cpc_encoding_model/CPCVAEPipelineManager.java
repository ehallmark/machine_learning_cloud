package models.similarity_models.cpc_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Setter;
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
public class CPCVAEPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "cpc_autoencoder";
    public static final int MAX_CPC_DEPTH = 4;
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("cpc_vae_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"cpc_vae_predictions/predictions_map.jobj");
    protected Map<String,? extends Collection<CPC>> cpcMap;
    @Setter
    protected CPCHierarchy hierarchy;
    protected Map<String,Integer> cpcToIdxMap;
    protected String modelName;
    protected int maxCPCDepth;
    public CPCVAEPipelineManager(String modelName) {
        this(modelName,INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,MAX_CPC_DEPTH);
    }

    protected CPCVAEPipelineManager(String modelName, File inputDataFolder, File predictionDataFile, int maxCPCDepth) {
        super(inputDataFolder,predictionDataFile);
        this.modelName=modelName;
        this.maxCPCDepth=maxCPCDepth;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new CPCVariationalAutoEncoderNN(this, modelName, maxCPCDepth);
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

    @Override
    public void rebuildPrerequisiteData() {
        hierarchy = CPCHierarchy.updateAndGetLatest();
    }

    @Override
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
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
                            .filter(cpc -> cpc.getNumParts() <= maxCPCDepth)
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

        RecursiveTask<CPCHierarchy> hierarchyTask = new RecursiveTask<CPCHierarchy>() {
            @Override
            protected CPCHierarchy compute() {
                return getHierarchy();
            }
        };

        cpcToIdxMap = CPCIndexMap.loadOrCreateMapForDepth(hierarchyTask,maxCPCDepth);

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
        System.out.println("Num training: "+trainAssets.size());
        System.out.println("Num test: "+testAssets.size());
        System.out.println("Num validation: "+validationAssets.size());
    }


    protected DataSetIterator getRawIterator(List<String> assets, boolean test) {
        boolean shuffle = !test;
        return new CPCDataSetIterator(assets,shuffle,BATCH_SIZE,cpcMap,cpcToIdxMap);
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 2;
        String modelName = MODEL_NAME;

        setLoggingLevel(Level.INFO);
        CPCVAEPipelineManager pipelineManager = new CPCVAEPipelineManager(modelName);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
