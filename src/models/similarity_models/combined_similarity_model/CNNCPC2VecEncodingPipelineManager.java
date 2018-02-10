package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CNNCPC2VecEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray>  {

    public static final String MODEL_NAME = "deep_cpc_cnn_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_CPC = new File("deep_cpc_single_2_vec_encoding_input_data");
    private static final File INPUT_DATA_FOLDER_WORD = new File("deep_cpc_word_2_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 64;
    protected static final Random rand = new Random(235);
    private static CNNCPC2VecEncodingPipelineManager MANAGER;
    protected String modelName;
    protected WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    private boolean trainOnWords;
    public CNNCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, boolean trainOnWords) {
        super(trainOnWords?INPUT_DATA_FOLDER_WORD:INPUT_DATA_FOLDER_CPC,PREDICTION_FILE);
        this.word2Vec=word2Vec;
        this.trainOnWords=trainOnWords;
        this.modelName=modelName;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    public CNNCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        this(modelName,word2Vec,wordCPC2VecPipelineManager,false);
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new CNNCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
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

    @Override
    public void rebuildPrerequisiteData() {

    }

    @Override
    public synchronized DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(dataFolder,MINI_BATCH_SIZE,true);
            manager.setMultiDataSetPreProcessor(new MultiDataSetPreProcessor() {
                @Override
                public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                    int[] prevShape = dataSet.getFeatures(0).shape();
                    int[] shape = new int[]{prevShape[0],prevShape[1]*prevShape[2]};
                    // set indices 2 and 3
                    dataSet.setFeatures(0, dataSet.getFeatures(0).reshape(shape));
                    dataSet.setLabels(dataSet.getFeatures());
                    dataSet.setLabelsMaskArray(null);
                    dataSet.setFeaturesMaskArrays(null);
                    //dataSet.setLabelsMaskArray(dataSet.getFeaturesMaskArrays());


                    /*INDArray featureMask = dataSet.getFeaturesMaskArray(0);
                    int[] lastIdx = featureMask.sum(1).data().asInt();
                    float[][] labelMaskArr = new float[featureMask.rows()][];
                    for(int i = 0; i < lastIdx.length; i++) {
                        float[] zeros = new float[featureMask.columns()];
                        zeros[lastIdx[i]-1] = 1f;
                        labelMaskArr[i]=zeros;
                    }
                    dataSet.setLabelsMaskArray(new INDArray[]{Nd4j.create(labelMaskArr)});*/
                }
            });
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

    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    protected int getMaxSamples() {
        return 8;
    }

    protected MultiDataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, int batch) {
        return new Word2VecToCPCIterator(iterator,word2Vec,batch,getMaxSamples());
    }

    @Override
    protected void setDatasetManager() {
        if(trainOnWords) {
            File baseDir = FileTextDataSetIterator.BASE_DIR;
            File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
            File devFile = new File(baseDir, getDevFile().getName());

            boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
            System.out.println("Using full text: "+fullText);

            WordCPCIterator trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);
            WordCPCIterator testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);
            WordCPCIterator devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples()*2, fullText);

            trainIter.setRunVocab(false);
            testIter.setRunVocab(false);
            devIter.setRunVocab(false);

            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                    dataFolder,
                    getRawIterator(trainIter,getBatchSize()),
                    getRawIterator(testIter, 1024),
                    getRawIterator(devIter, 1024),
                    true
            );
            manager.setMultiDataSetPreProcessor(new MultiDataSetPreProcessor() {
                @Override
                public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                    dataSet.setLabels(null);
                    dataSet.setLabelsMaskArray(null);
                }
            });
            datasetManager = manager;

        } else {
            int trainLimit = 5000000;
            int testLimit = 30000;
            int devLimit = 30000;
            Random rand = new Random(235);

            Map<String, Collection<CPC>> cpcMap = wordCPC2VecPipelineManager.getCPCMap();

            List<List<String>> entries = cpcMap.keySet().stream().flatMap(asset -> {
                Collection<CPC> cpcs = cpcMap.get(asset);
                if (cpcs == null) return Stream.empty();
                List<String> cpcLabels = cpcs.stream()
                        .filter(cpc -> word2Vec.getVocab().indexOf(cpc.getName()) >= 0)
                        .map(cpc -> cpc.getName())
                        .collect(Collectors.toCollection(ArrayList::new));

                if (cpcLabels.isEmpty()) return Stream.empty();
                return IntStream.range(0, Math.min(3, Math.max(1, Math.round((float) Math.log(cpcLabels.size())))))//cpcLabels.size())
                        .mapToObj(i -> {
                            List<String> cpcLabelsClone = new ArrayList<>(cpcLabels);
                            Collections.shuffle(cpcLabelsClone);
                            if (cpcLabelsClone.size() > getMaxSamples())
                                cpcLabelsClone = cpcLabelsClone.subList(0, getMaxSamples());
                            return new Pair<>(asset, cpcLabelsClone);
                        });
            }).filter(e -> e != null).map(e -> e.getSecond()).collect(Collectors.toList());

            final int numAssets = entries.size();
            Collections.shuffle(entries, rand);

            final int[] trainIndices = new int[Math.min(numAssets - testLimit - devLimit, trainLimit)];
            final int[] testIndices = new int[testLimit];
            final int[] devIndices = new int[devLimit];

            Set<Integer> seenIndex = new HashSet<>();
            for (int i = 0; i < testLimit; i++) {
                int next = rand.nextInt(numAssets);
                if (seenIndex.contains(next)) {
                    i--;
                    continue;
                } else {
                    testIndices[i] = next;
                    seenIndex.add(i);
                }
            }
            for (int i = 0; i < devLimit; i++) {
                int next = rand.nextInt(numAssets);
                if (seenIndex.contains(next)) {
                    i--;
                    continue;
                } else {
                    devIndices[i] = next;
                    seenIndex.add(i);
                }
            }
            int i = 0;
            int idx = 0;
            System.out.println("Starting to find train indices...");
            while (idx < trainIndices.length) {
                if (!seenIndex.contains(i)) {
                    trainIndices[idx] = i;
                    idx++;
                }
                i++;
            }


            INDArray[] trainVectors = buildVectors(trainIndices, entries);
            INDArray[] testVectors = buildVectors(testIndices, entries);
            INDArray[] devVectors = buildVectors(devIndices, entries);

            System.out.println("Finished finding test/train/dev indices...");
            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                    dataFolder,
                    new VocabSamplingIterator(trainVectors, 2 * trainLimit, getBatchSize(), true),
                    new VocabSamplingIterator(testVectors, -1, 1024, false),
                    new VocabSamplingIterator(devVectors, -1, 1024, false),
                    true
            );
            manager.setMultiDataSetPreProcessor(new MultiDataSetPreProcessor() {
                @Override
                public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                    dataSet.setLabels(null);
                    dataSet.setLabelsMaskArray(null);
                }
            });
            datasetManager = manager;
        }
    }

    private INDArray[] buildVectors(int[] indices, List<List<String>> _entries) {
        List<List<String>> entries = IntStream.of(indices).mapToObj(i->_entries.get(i)).collect(Collectors.toList());
        return buildVectors(entries,word2Vec,getMaxSamples());
    }

    public static INDArray[] buildVectors(List<List<String>> entries, Word2Vec word2Vec, int sample) {
        System.out.println("Starting to build vectors... Num entries: "+entries.size());
        INDArray[] vectors = entries.stream().map(cpcLabels->{
            int numCPCLabels = cpcLabels.size();
            if(sample>numCPCLabels) {
                return null;
            } else {
                return word2Vec.getWordVectors(cpcLabels).transpose();
            }
        }).filter(vec->vec!=null).toArray(size->new INDArray[size]);

        System.out.println("Finished. Now creating indices...");

        return vectors;
    }


    public static synchronized CNNCPC2VecEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec, boolean trainOnWords) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new CNNCPC2VecEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager,trainOnWords);
        }
        return MANAGER;
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        boolean trainOnWords = false;
        int nEpochs = 3;

        if(trainOnWords && !INPUT_DATA_FOLDER_WORD.exists()&&runModels) {
            rebuildDatasets=true;
        } else if (!trainOnWords && !INPUT_DATA_FOLDER_CPC.exists()&&runModels) {
            rebuildDatasets=true;
        }

        CNNCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets,trainOnWords);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
