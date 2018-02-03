package models.similarity_models.word_cpc_2_vec_model;

import data_pipeline.models.WordVectorPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.CBOW;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPC2VecModel extends WordVectorPredictionModel<Map<String,INDArray>> {
    public static final String WORD_VECTORS = "wordVectors";
    public static final String CLASS_VECTORS = "cpcVectors";
    private static final int BATCH_SIZE = 512;
    private static Type MODEL_TYPE = Type.Word2Vec;
    public static final File BASE_DIR = new File("wordcpc2vec_model_data");

    private WordCPC2VecPipelineManager pipelineManager;
    private final int vectorSize;
    public WordCPC2VecModel(WordCPC2VecPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(modelName+MODEL_TYPE+vectorSize,MODEL_TYPE);
        this.pipelineManager=pipelineManager;
        this.vectorSize=vectorSize;
    }

    @Override
    public Map<String, Map<String,INDArray>> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        Map<String,Map<String,INDArray>> predictions = Collections.synchronizedMap(new HashMap<>());

        Set<String> allClassCodes = Collections.synchronizedSet(new HashSet(classCodes));

        WeightLookupTable<VocabWord> lookupTable = net.lookupTable();
        Collection<VocabWord> vocabulary = net.vocab().tokens();
        System.out.println("Vocabulary size: "+vocabulary.size());

        Map<String,INDArray> classCodeVectors = Collections.synchronizedMap(new HashMap<>());
        Map<String,INDArray> wordVectors = Collections.synchronizedMap(new HashMap<>());
        vocabulary.parallelStream().forEach(vocabWord -> {
            INDArray vec = lookupTable.vector(vocabWord.getLabel());
            if(vec!=null) {
                if (allClassCodes.contains(vocabWord.getLabel())) {
                    classCodeVectors.put(vocabWord.getLabel(),vec);
                } else {
                    wordVectors.put(vocabWord.getLabel(),vec);
                }
            }
        });

        System.out.println("Vocabulary size: "+vocabulary.size());
        System.out.println("Num cpcs: "+classCodeVectors.size());
        System.out.println("Num words: "+wordVectors.size());

        predictions.put(CLASS_VECTORS,classCodeVectors);
        predictions.put(WORD_VECTORS,wordVectors);

        return predictions;
    }


    @Override
    public void train(int nEpochs) {
        int vocabSampling = -1;

        WordCPCIterator iterator = pipelineManager.getDatasetManager().getTrainingIterator();
        iterator.setVocabSampling(vocabSampling);

        Collection<String> words = pipelineManager.getTestWords();
        int windowSize = pipelineManager.getWindowSize();
        int minWordFrequency = 5;
        double negativeSampling = -1;
        double sampling = 0.0001;
        double learningRate = 0.01;
        double minLearningRate = 0.0001;
        int testIterations = 4000000;

        AtomicInteger nTestsCounter = new AtomicInteger(0);
        final int saveEveryNTests = 5;
        Function<SequenceVectors<VocabWord>,Void> saveFunction = sequenceVectors->{
            if(nTestsCounter.getAndIncrement()%saveEveryNTests==saveEveryNTests-1) {
                System.out.println("Saving...");
                double score = 0d;
                try {
                    save(LocalDateTime.now(), score, sequenceVectors);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        };

        boolean newModel = net == null;
        Word2Vec.Builder builder = new Word2Vec.Builder()
                .seed(41)
                .batchSize(BATCH_SIZE)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(windowSize)
                .layerSize(vectorSize)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(minLearningRate)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(newModel)
                .minWordFrequency(minWordFrequency)
                .workers(Math.max(1,Runtime.getRuntime().availableProcessors()))
                .iterations(1)
                .setVectorsListeners(Collections.singleton(new CustomWordVectorListener(saveFunction,modelName,testIterations,words.toArray(new String[]{}))))
                .useHierarchicSoftmax(true)
                .stopWords(Collections.emptySet())
                //.trainElementsRepresentation(true)
                //.trainSequencesRepresentation(true)
                //.sequenceLearningAlgorithm(new DBOW<>())
                .elementsLearningAlgorithm(new CBOW<>())
                .iterate(iterator);

        if(!newModel) {
            iterator.setRunVocab(false);
            builder = builder
                    .vocabCache(net.vocab())
                    .lookupTable(net.lookupTable());
        }

        net = builder.build();

        net.fit();

        System.out.println("Testing...");
        for (String word : words) {
            INDArray vec = net.lookupTable().vector(word);
            if(vec!=null) {
                Collection<String> lst = net.wordsNearest(vec, 10);
                System.out.println("10 Words closest to '" + word + "': " + lst);

                if (net instanceof ParagraphVectors) {
                    ParagraphVectors pv = (ParagraphVectors) net;
                    Collection<String> topLabels = pv.nearestLabels(vec, 10);
                    System.out.println("10 Labels closest to '" + word + "': " + topLabels);
                }
            }
        }

        System.out.println("Saving...");
        double score = 0d;
        try {
            save(LocalDateTime.now(), score, net);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (CustomWordVectorListener.class) {
            System.out.println("Everything should be saved.");
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
