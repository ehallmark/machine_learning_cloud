package models.similarity_models.cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.PipelineManager;
import data_pipeline.vectorize.DatasetManager;
import models.dl4j_neural_nets.iterators.datasets.AsyncDataSetIterator;
import models.similarity_models.signatures.CPCDataSetIterator;
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
public class CPCVAEPipelineManager implements PipelineManager {
    public static final int MAX_CPC_DEPTH = 4;
    private static final int BATCH_SIZE = 128;
    private static final File dataFolder = new File("cpc_vae_data");
    private static final File cpcToIdxMapFile = new File(Constants.DATA_FOLDER+"cpc_vae_cpc_to_idx_map.jobj");
    private transient DatasetManager datasetManager;
    private transient Map<String,? extends Collection<CPC>> cpcMap;
    private transient CPCHierarchy hierarchy;
    private Map<String,Integer> cpcToIdxMap;

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
            cpcToIdxMap = (Map<String,Integer>)Database.tryLoadObject(cpcToIdxMapFile);
        }
        if(cpcToIdxMap == null) {
            System.out.println("WARNING: NO CPC to Index Map Found.");
        }
        return cpcToIdxMap;
    }

    private synchronized void saveIdxMap() {
        Database.trySaveObject(cpcToIdxMap,cpcToIdxMapFile);
    }

    @Override
    public void loadRawDatasets() {
        System.out.println("Starting to recreate datasets...");
        int limit = 5000000;
        List<String> allAssets;
        List<String> testAssets;
        List<String> validationAssets;
        List<String> trainAssets;
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

        System.out.println("Finished splitting test and train.");
        System.out.println("Num training: "+trainAssets.size());
        System.out.println("Num test: "+testAssets.size());
        System.out.println("Num validation: "+validationAssets.size());

        if(!dataFolder.exists()) dataFolder.mkdir();
        datasetManager = new DatasetManager(dataFolder,
                getRawIterator(trainAssets, false),
                getRawIterator(testAssets,true),
                getRawIterator(validationAssets, true)
        );
        datasetManager.removeDataFromDisk();
        System.out.println("Saving datasets...");
        datasetManager.saveDataSets();
    }

    private DataSetIterator getRawIterator(List<String> assets, boolean test) {
        boolean shuffle = !test;
        return new AsyncDataSetIterator(new CPCDataSetIterator(assets,shuffle,test ? 1024 : BATCH_SIZE,cpcMap,cpcToIdxMap), Runtime.getRuntime().availableProcessors()/2);
    }

    public static void main(String[] args) throws Exception {
        // start with data pipeline
        boolean recreateDatasets = true;
        boolean recreateNeuralNetworks = true;

        CPCVAEPipelineManager pipelineManager = new CPCVAEPipelineManager();

        // STAGE 1 of pipeline: LOAD DATA
        if(recreateDatasets) {
            pipelineManager.loadRawDatasets();
        }

        // STAGE 2 of pipeline: TRAINING
        int nEpochs = 5;
        int maxCPCDepth = pipelineManager.getMaxCpcDepth();

        CPCVariationalAutoEncoderNN model;
        if(!recreateNeuralNetworks) {
            System.out.println("Warning: Using previous model.");
            model = CPCVariationalAutoEncoderNN.restoreAndInitModel(maxCPCDepth,true);
        } else {
            model = new CPCVariationalAutoEncoderNN();
        }
        model.train(nEpochs);
        if(!model.isSaved()) {
            model.save();
        }

        // test restore model
        System.out.println("Restoring model");
        CPCVariationalAutoEncoderNN clone = CPCVariationalAutoEncoderNN.restoreAndInitModel(maxCPCDepth,true);

    }

}
