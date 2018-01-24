package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import lombok.Setter;
import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.stages.Stage1;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPC2VecPipelineManager extends DefaultPipelineManager<WordCPCIterator,Map<String,INDArray>> {
    public static final String SMALL_MODEL_NAME = "32smallwordcpc2vec_model";
    public static final String LARGE_MODEL_NAME = "wordcpc2vec_model_large";
    public static final String MODEL_NAME = "wordcpc2vec2_model";
    public static final String OLD_MODEL_NAME = "wordcpc2vec_filing_model";
    public static final File cpcMapFile = new File(Constants.DATA_FOLDER+"word_cpc_2_vec_cpcmap_file.jobj");
    private static final int SMALL_VECTOR_SIZE = 32;
    private static final int LARGE_VECTOR_SIZE = 128;
    public static final Map<String,Integer> modelNameToVectorSizeMap = Collections.synchronizedMap(new HashMap<>());
    static {
        modelNameToVectorSizeMap.put(SMALL_MODEL_NAME,SMALL_VECTOR_SIZE);
        modelNameToVectorSizeMap.put(LARGE_MODEL_NAME,LARGE_VECTOR_SIZE);
        modelNameToVectorSizeMap.put(MODEL_NAME,LARGE_VECTOR_SIZE);
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
    @Getter @Setter
    private int maxSamples;
    @Getter
    private int windowSize;
    public WordCPC2VecPipelineManager(String modelName, int numEpochs, int windowSize, int maxSamples) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.modelName=modelName;
        this.windowSize=windowSize;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00","semiconductor","computer","internet","virtual","intelligence","artificial","chemistry","biology","electricity","agriculture","automobile","robot");
    }

    @Override
    public void rebuildPrerequisiteData() {
        try {
            System.out.println("Starting to pull latest text data from elasticsearch...");
            //ESTextDataSetIterator.main(null);
            System.out.println("Starting to build vocab map...");
            Stage1 stage1 = new Stage1(KeyphrasePredictionPipelineManager.modelParams);
            stage1.run(true);

        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public Map<String,INDArray> getOrLoadWordVectors() {
        Map<String,Map<String,INDArray>> predictions = loadPredictions();
        if(predictions==null) return null;
        return predictions.get(WordCPC2VecModel.WORD_VECTORS);
    }

    public Map<String,INDArray> getOrLoadCPCVectors() {
        Map<String,Map<String,INDArray>> predictions = loadPredictions();
        if(predictions==null) return null;
        return predictions.get(WordCPC2VecModel.CLASS_VECTORS);
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

            cpcMap = (Map<String,Collection<CPC>>) Database.tryLoadObject(cpcMapFile);

            if(cpcMap == null) {
                Map<String, String> patentToFiling = new AssetToFilingMap().getPatentDataMap();
                Map<String, String> appToFiling = new AssetToFilingMap().getApplicationDataMap();
                getHierarchy();
                Map<String, Set<String>> assetToCPCStringMap = new HashMap<>();
                new AssetToCPCMap().getApplicationDataMap().entrySet().forEach(e -> {
                    assetToCPCStringMap.put(appToFiling.get(e.getKey()), e.getValue());
                });
                new AssetToCPCMap().getPatentDataMap().entrySet().forEach(e -> {
                    assetToCPCStringMap.put(patentToFiling.get(e.getKey()), e.getValue());
                });
                cpcMap = assetToCPCStringMap.entrySet().parallelStream()
                        .filter(e -> assetToCPCStringMap.containsKey(e.getKey()))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().map(label -> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                                .filter(cpc -> cpc != null)
                                .flatMap(cpc -> hierarchy.cpcWithAncestors(cpc).stream())
                                .distinct()
                                .collect(Collectors.toSet())))
                        .entrySet().parallelStream()
                        .filter(e -> e.getValue().size() > 0)
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

                Database.trySaveObject(cpcMap,cpcMapFile);
            }

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
            File baseDir = FileTextDataSetIterator.BASE_DIR;
            File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
            File devFile = new File(baseDir, FileTextDataSetIterator.devFile2.getName());

            FileTextDataSetIterator trainIter = new FileTextDataSetIterator(trainFile);
            FileTextDataSetIterator testIter = new FileTextDataSetIterator(testFile);
            FileTextDataSetIterator devIter = new FileTextDataSetIterator(devFile);

            boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
            System.out.println("Using full text: "+fullText);
            datasetManager = new NoSaveDataSetManager<>(
                    new WordCPCIterator(trainIter,numEpochs,getCPCMap(), windowSize, maxSamples,fullText),
                    new WordCPCIterator(testIter,1,getCPCMap(), windowSize, maxSamples,fullText),
                    new WordCPCIterator(devIter,1,getCPCMap(), windowSize, maxSamples,fullText)
            );
        }
    }


    public static void main(String[] args) throws Exception {
        boolean runSmallModel = true;
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        final int maxSamples;
        final int windowSize;
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 5;

        String modelName;
        if(runSmallModel) {
            windowSize = 6;
            modelName = SMALL_MODEL_NAME;
            maxSamples = 20;
        } else {
            windowSize = 4;
            modelName = MODEL_NAME;
            maxSamples = 300;
        }

        WordCPC2VecPipelineManager pipelineManager = new WordCPC2VecPipelineManager(modelName,nEpochs,windowSize,maxSamples);
        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
