package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import data_pipeline.vectorize.CombinedFileMultiMinibatchIterator;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepCPC2VecEncodingPipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "deep_cpc_rnn6_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("deep_cpc_all3_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 32;
    private static final int MAX_NETWORK_RECURSION = -1;
    private static int MAX_SAMPLE = 6;
    protected static final Random rand = new Random(235);
    private static DeepCPC2VecEncodingPipelineManager MANAGER;
    @Getter
    private static final ReentrantLock lock = new ReentrantLock();
    public DeepCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,MAX_SAMPLE,wordCPC2VecPipelineManager);
    }

    public static String currentDataFolderName(int recursion,int sample) {
        return (INPUT_DATA_FOLDER_ALL).getAbsolutePath()+sample+(recursion>0?("_r"+recursion):"");
    }

    public static String currentDataFolderName(int sample) {
        return currentDataFolderName(-1,sample);
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new DeepCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
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
    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                dataSet.setLabels(null);
                dataSet.setLabelsMaskArray(null);
            }
        };
    }

    @Override
    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        Random rand = new Random(59);
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                ComputationGraph encoder = ((DeepCPC2VecEncodingModel) model).getVaeNetwork();
                dataSet.setLabels(dataSet.getFeatures().clone());
                INDArray newFeatures = dataSet.getFeatures(0);
                int r = MAX_NETWORK_RECURSION >= 0 ? rand.nextInt(MAX_NETWORK_RECURSION) : 0;
                for (int i = 0; i < r; i++) {
                    //System.out.println("Shape before: "+Arrays.toString(newFeatures.shape()));
                    lock.lock();
                    try {
                        newFeatures = encoder.output(false, newFeatures)[0];

                    } catch(Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("EXCEPTION DURING PRE CODE");
                        //System.exit(1);
                    } finally {
                        lock.unlock();
                    }
                   // System.out.println("Shape time "+i+": "+Arrays.toString(newFeatures.shape()));
                }
                dataSet.setFeatures(0, newFeatures);
                dataSet.setLabelsMaskArray(null);
                dataSet.setFeaturesMaskArrays(null);
            }
        };
    }

    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static synchronized DeepCPC2VecEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new DeepCPC2VecEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager);
        }
        return MANAGER;
    }


    protected MultiDataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, int batch) {
        return new Word2VecToCPCIterator(iterator,word2Vec,batch,getMaxSamples());
    }

    @Override
    protected void setDatasetManager() {
        MultiDataSetIterator train1;
        MultiDataSetIterator train2;
        MultiDataSetIterator test1;
        MultiDataSetIterator test2;
        MultiDataSetIterator val1;
        MultiDataSetIterator val2;

        {
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

            train1 =  getRawIterator(trainIter,getBatchSize());
            test1 =  getRawIterator(testIter,1024);
            val1 =  getRawIterator(devIter,1024);



        }
        {
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

                if (cpcLabels.size()<getMaxSamples()) return Stream.empty();
                return IntStream.range(0, Math.min(5, Math.max(1, Math.round((float) Math.log(cpcLabels.size())))))//cpcLabels.size())
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

            Function<Integer,INDArray> buildVectorFunction = index -> buildVectors(new int[]{index},entries)[0];

            train2 = new VocabSamplingIterator(trainIndices,buildVectorFunction,vectorSize,maxSample,2*trainLimit,getBatchSize(), true);
            test2 = new VocabSamplingIterator(testIndices, buildVectorFunction,vectorSize,maxSample,-1, 1024, false);
            val2 = new VocabSamplingIterator(devIndices, buildVectorFunction,vectorSize,maxSample,-1, 1024, false);
        }


        PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                dataFolder,
                new CombinedFileMultiMinibatchIterator(train1,train2),
                new CombinedFileMultiMinibatchIterator(test1,test2),
                new CombinedFileMultiMinibatchIterator(val1,val2),
                true
        );
        manager.setMultiDataSetPreProcessor(getSeedTimeMultiDataSetPreProcessor());
        datasetManager = manager;
    }

    private INDArray[] buildVectors(int[] indices, List<List<String>> _entries) {
        List<List<String>> entries = IntStream.of(indices).mapToObj(i->_entries.get(i)).collect(Collectors.toList());
        return buildVectors(entries,word2Vec,getMaxSamples());
    }

    public static INDArray[] buildVectors(List<List<String>> entries, Word2Vec word2Vec, int sample) {
        INDArray[] vectors = entries.stream().map(cpcLabels->{
            int numCPCLabels = cpcLabels.size();
            if(sample>numCPCLabels) {
                return null;
            } else {
                return word2Vec.getWordVectors(cpcLabels).transpose();
            }
        }).filter(vec->vec!=null).toArray(size->new INDArray[size]);
        return vectors;
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
        int nEpochs = 3;

        rebuildDatasets = runModels && !new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)).exists();

        DeepCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
