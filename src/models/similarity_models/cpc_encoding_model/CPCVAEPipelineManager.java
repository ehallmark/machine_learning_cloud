package models.similarity_models.cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.pipeline_manager.PipelineManager;
import data_pipeline.vectorize.DatasetManager;
import models.dl4j_neural_nets.iterators.datasets.AsyncDataSetIterator;
import models.similarity_models.signatures.CPCDataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
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
    private static final File DATA_FOLDER = new File("cpc_vae_data");
    private static final File PREDICTION_DATA_FOLDER = new File(Constants.DATA_FOLDER+DATA_FOLDER.getName()+"_predictions_map.jobj");
    private static final File CPC_TO_INDEX_FILE = new File(Constants.DATA_FOLDER+"cpc_vae_cpc_to_idx_map.jobj");
    private Map<String,? extends Collection<CPC>> cpcMap;
    private CPCHierarchy hierarchy;
    private Map<String,Integer> cpcToIdxMap;

    public CPCVAEPipelineManager() {
        super(DATA_FOLDER,PREDICTION_DATA_FOLDER);
    }

    protected void initModel(boolean forceRecreateModels) {
        int maxCPCDepth = getMaxCpcDepth();
        if(!forceRecreateModels) {
            System.out.println("Warning: Using previous model.");
            try {
                model = CPCVariationalAutoEncoderNN.restoreAndInitModel(maxCPCDepth, true, this);
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
                model = null;
            }
        }
        if(model == null) {
            model = new CPCVariationalAutoEncoderNN(this);
        }
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

    public int getMaxCpcDepth() {
        return MAX_CPC_DEPTH;
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

    public synchronized Map<String,Integer> getOrLoadIdxMap() {
        if(cpcToIdxMap == null) {
            cpcToIdxMap = (Map<String,Integer>)Database.tryLoadObject(CPC_TO_INDEX_FILE);
        }
        if(cpcToIdxMap == null) {
            System.out.println("WARNING: NO CPC to Index Map Found.");
        }
        return cpcToIdxMap;
    }

    private synchronized void saveIdxMap() {
        Database.trySaveObject(cpcToIdxMap,CPC_TO_INDEX_FILE);
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        int limit = 5000000;
        List<String> allAssets;
        getHierarchy();

        System.out.println("WARNING: Reindexing CPC Codes...");
        AtomicInteger idx = new AtomicInteger(0);
        cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e->e.getValue().getNumParts()<=MAX_CPC_DEPTH).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
        System.out.println("Input size: " + cpcToIdxMap.size());
        saveIdxMap();

        getCPCMap();
        System.out.println("Loaded cpcMap");
        allAssets = new ArrayList<>(cpcMap.keySet().parallelStream().filter(asset->cpcMap.containsKey(asset)).sorted().collect(Collectors.toList()));

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
        return new AsyncDataSetIterator(new CPCDataSetIterator(assets,shuffle,test ? 1024 : BATCH_SIZE,cpcMap,cpcToIdxMap), Runtime.getRuntime().availableProcessors()/4);
    }

    public static void main(String[] args) throws Exception {
        CPCVAEPipelineManager pipelineManager = new CPCVAEPipelineManager();
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 1;

        pipelineManager.runPipeline(rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
