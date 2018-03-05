package models.cpc_generalizer;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.combined_similarity_model.CombinedCPC2Vec2VAEEncodingModel;
import models.similarity_models.combined_similarity_model.CombinedCPC2Vec2VAEEncodingPipelineManager;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.AbstractWordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/12/2018.
 */
public class CPCGeneralizationPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    public static final String MODEL_NAME = "cpc_generalization_model";
    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"cpc_generalization_model_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER = new File("cpc_generalization_model_input_data");
    private static final int VECTOR_SIZE = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 128;
    protected static final Random rand = new Random(235);
    private static CPCGeneralizationPipelineManager MANAGER;
    protected String modelName;
    protected AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    public CPCGeneralizationPipelineManager(String modelName, Word2Vec word2Vec, AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_FILE);
        this.word2Vec=word2Vec;
        System.out.println("Initializing "+modelName);
        this.modelName=modelName;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    public CPCGeneralizationPipelineManager(File dataFolder, File finalPredictionsFile, String modelName) {
        super(dataFolder, finalPredictionsFile);
        this.modelName = modelName;
    }


    @Override
    public void rebuildPrerequisiteData() {

    }

    @Override
    public synchronized DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(dataFolder,MINI_BATCH_SIZE,true);
            manager.setMultiDataSetPreProcessor(getTrainTimeMultiDataSetPreProcessor());
            datasetManager = manager;
            //setDatasetManager();
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }


    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new CPCGeneralizationModel(this,modelName,VECTOR_SIZE);
        }
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadMostRecentModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }


    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(MultiDataSet dataSet) {
                //INDArray labels = dataSet.getLabels(0);
                //INDArray newLabels = Nd4j.zeros(labels.shape()[0],labels.shape()[1],dataSet.getFeatures(0).shape()[2]);
                //newLabels.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(newLabels.shape()[2]-1)).assign(labels);
                //INDArray labelMask = Nd4j.zeros(labels.shape()[0],newLabels.shape()[2]);
                //labelMask.get(NDArrayIndex.all(),NDArrayIndex.point(labelMask.columns()-1)).assign(1);
                //dataSet.setLabelsMaskArray(new INDArray[]{labelMask});
                //dataSet.setLabels(0,newLabels);
                //System.out.println("Label shape: "+ Arrays.toString(newLabels.shape()));
            }
        };
    }


    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
            }
        };
    }


    public static synchronized CPCGeneralizationPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;

            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new CPCGeneralizationPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager);
        }
        return MANAGER;
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            CPCHierarchy hierarchy = new CPCHierarchy();
            hierarchy.loadGraph();

            Map<String, INDArray> cpcVectors = wordCPC2VecPipelineManager.getOrLoadCPCVectors();

            Map<String,Long> cpcToAppeared = Database.getPatentToClassificationMap()
                    .values().parallelStream().flatMap(l->l.stream())
                    .collect(Collectors.groupingBy(e->e,Collectors.counting()));

            List<String> cpcs = cpcToAppeared.keySet().stream()
                    .filter(cpcVectors::containsKey)
                    .sorted()
                    .collect(Collectors.toList());

            Map<String,String> cpcToParentMap = cpcs.parallelStream()
                    .map(cpc->hierarchy.getLabelToCPCMap().get(cpc))
                    .filter(cpc->cpc!=null&&cpc.getParent()!=null&&cpcVectors.containsKey(cpc.getParent().getName()))
                    .collect(Collectors.toMap(cpc->cpc.getName(),cpc->cpc.getParent().getName()));

            cpcs = new ArrayList<>(cpcToParentMap.keySet());
            Collections.shuffle(cpcs);

            int numTests = 15000;
            List<String> trainCpcs = cpcs.subList(numTests*2,cpcs.size());
            List<String> testCpcs = cpcs.subList(0,numTests);
            List<String> devCpcs = cpcs.subList(numTests,numTests*2);

            double[] cpcProbs = new double[trainCpcs.size()];
            double sum = 0d;
            for(int i = 0; i < cpcProbs.length; i++) {
                cpcProbs[i]=cpcToAppeared.get(trainCpcs.get(i));
                sum += cpcProbs[i];
            }
            for(int i = 0; i < cpcProbs.length; i++) {
                cpcProbs[i]/=sum;
            }

            MultiDataSetIterator train = getRawIterator(trainCpcs, cpcProbs,hierarchy,1000000,getBatchSize());
            MultiDataSetIterator test = getRawIterator(testCpcs, null, hierarchy, numTests,1024);
            MultiDataSetIterator val = getRawIterator(devCpcs, null, hierarchy, numTests,1024);

            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                    dataFolder,
                    train,
                    test,
                    val,
                    true
            );
            manager.setMultiDataSetPreProcessor(getSeedTimeMultiDataSetPreProcessor());

            datasetManager = manager;
        }
    }

    protected MultiDataSetIterator getRawIterator(List<String> cpcs, double[] cpcProbs, CPCHierarchy hierarchy, int n, int batch) {
        return new CPCToParentIterator(cpcs,cpcProbs,hierarchy,word2Vec,batch,n);
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 5;

        rebuildDatasets = runModels && !INPUT_DATA_FOLDER.exists();

        CPCGeneralizationPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets||runPredictions);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }


}
