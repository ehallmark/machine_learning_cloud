package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.CombinedFileMultiMinibatchIterator;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 2/12/2018.
 */
public abstract class AbstractEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    protected String modelName;
    protected WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    protected int vectorSize;
    protected int batchSize;
    protected int miniBatchSize;
    protected int maxSample;
    public AbstractEncodingPipelineManager(File dataFolder, File predictionFile, String modelName, Word2Vec word2Vec, int vectorSize, int batchSize, int miniBatchSize, int maxSample, WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(dataFolder, predictionFile);
        this.word2Vec=word2Vec;
        System.out.println("Initializing "+modelName);
        this.miniBatchSize=miniBatchSize;
        this.modelName=modelName;
        this.maxSample=maxSample;
        this.batchSize=batchSize;
        this.vectorSize=vectorSize;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    @Override
    public void rebuildPrerequisiteData() {

    }

    @Override
    public synchronized DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(dataFolder,miniBatchSize,true);
            manager.setMultiDataSetPreProcessor(getMultiDataSetPreProcessor());
            datasetManager = manager;
            //setDatasetManager();
        }
        return datasetManager;
    }

    protected MultiDataSetPreProcessor getMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                dataSet.setLabels(dataSet.getFeatures());
                dataSet.setLabelsMaskArray(null);
                dataSet.setFeaturesMaskArrays(null);
            }
        };
    }

    public int getBatchSize() {
        return batchSize;
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
        return maxSample;
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

            train2 = new VocabSamplingIterator(trainVectors,2*trainLimit,getBatchSize(), true);
            test2 = new VocabSamplingIterator(testVectors, -1, 1024, false);
            val2 = new VocabSamplingIterator(devVectors, -1, 1024, false);
        }


        PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                dataFolder,
                new CombinedFileMultiMinibatchIterator(train1,train2),
                new CombinedFileMultiMinibatchIterator(test1,test2),
                new CombinedFileMultiMinibatchIterator(val1,val2),
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
}
