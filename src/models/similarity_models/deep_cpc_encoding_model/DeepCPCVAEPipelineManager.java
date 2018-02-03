package models.similarity_models.deep_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.cpc_encoding_model.CPCDataSetIterator;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepCPCVAEPipelineManager extends CPCVAEPipelineManager {
    public static final String MODEL_NAME = "deep64_cpc_autoencoder";
    public static final int MAX_CPC_DEPTH = 5;
    private static final int BATCH_SIZE = 256;
    private static final int MINI_BATCH_SIZE = -1;
    private static final int MIN_CPC_APPEARANCES = 420;
    private static final File INPUT_DATA_FOLDER = new File("deep_cpc_vae_data");
    private static final File PREDICTION_DATA_FILE = new File("deep_cpc_vae_predictions/predictions_map.jobj");

    public DeepCPCVAEPipelineManager(String modelName) {
        super(modelName,INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,MAX_CPC_DEPTH);
    }

    @Override
    protected void initModel(boolean forceRecreateModels) {
        model = new DeepCPCVariationalAutoEncoderNN(this, modelName, maxCPCDepth);
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
        if(trainAssets==null) splitData();
        PreSaveDataSetManager manager = new PreSaveDataSetManager<>(
                dataFolder,
                getRawIterator(trainAssets, false),
                getRawIterator(testAssets,true),
                getRawIterator(validationAssets, true),
                false
        );
        manager.setDataSetPreProcessor(new DataSetPreProcessor() {
            @Override
            public void preProcess(DataSet dataSet) {
                dataSet.setLabels(null);
            }
        });
        datasetManager=manager;
    }
    public int getMinCPCOccurrences() {
        return MIN_CPC_APPEARANCES;
    }

    @Override
    public void rebuildPrerequisiteData() {
        hierarchy = CPCHierarchy.updateAndGetLatest();
    }

    @Override
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            PreSaveDataSetManager manager = new PreSaveDataSetManager<>(dataFolder,MINI_BATCH_SIZE,false);
            manager.setDataSetPreProcessor(new DataSetPreProcessor() {
                @Override
                public void preProcess(DataSet dataSet) {
                    dataSet.setLabels(dataSet.getFeatures());
                }
            });
            datasetManager = manager;
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }


    @Override
    public synchronized Map<String,Collection<CPC>> getCPCMap() {
        if(cpcMap==null) {
            getHierarchy();
            getCpcToIdxMap();

            Set<String> allAssets = new HashSet<>(Database.getAllFilings());
            Map<String,Set<String>> assetToCPCStringMap = Collections.synchronizedMap(new HashMap<>(new AssetToCPCMap().getApplicationDataMap()));
            assetToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());

            cpcMap = allAssets.parallelStream()
                    .map(filing->new Pair<>(filing,
                            Stream.of(new FilingToAssetMap().getPatentDataMap().getOrDefault(filing,Collections.emptyList()),new FilingToAssetMap().getApplicationDataMap().getOrDefault(filing,Collections.emptyList()))
                                    .flatMap(list->list.stream()).filter(asset->asset!=null)
                                    .flatMap(asset->assetToCPCStringMap.getOrDefault(asset,Collections.emptySet()).stream()).collect(Collectors.toSet())
                            )
                    ).filter(p->p!=null&&p.getSecond().size()>0)
                    .collect(Collectors.toMap(e->e.getFirst(), e ->
                                    e.getSecond().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
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
        System.out.println("CPC Map size: "+cpcMap.size());
        return cpcMap;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        int limit = 2500000;
        int numTest = 25000;
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


    @Override
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
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = MODEL_NAME;

        setCudaEnvironment();

        setLoggingLevel(Level.INFO);
        DeepCPCVAEPipelineManager pipelineManager = new DeepCPCVAEPipelineManager(modelName);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
