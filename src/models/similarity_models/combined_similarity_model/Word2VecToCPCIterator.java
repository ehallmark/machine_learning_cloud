package models.similarity_models.combined_similarity_model;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import org.apache.commons.lang.ArrayUtils;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class Word2VecToCPCIterator implements MultiDataSetIterator {
    private static Random rand = new Random(23);
    public static final Function<List<VocabWord>,Map<String,Integer>> groupingBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.groupingBy(word->word.getLabel(), Collectors.summingInt(label->(int)label.getElementFrequency())));
    };
    private static Function<List<VocabWord>,Map<String,Integer>> countBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.toMap(word->word.getLabel(),word->(int)word.getElementFrequency()));
    };


    private SequenceIterator<VocabWord> documentIterator;
    private Vectorizer vectorizer;
    private Word2Vec word2Vec;
    private int batch;
    private MultiDataSet currentDataSet;
    private int numDimensions;
    private double numDocs;
    private boolean requireLabel;
    public Word2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, long numDocs, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize, boolean requireLabel, int numOutcomes) {
        this.documentIterator=documentIterator;
        this.requireLabel=requireLabel;
        this.vectorizer = cpcEncodings==null?null: new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.numDocs = numDocs;
        this.numDimensions=numOutcomes;
        reset();
    }

    @Override
    public MultiDataSet next(int batch) {
        return currentDataSet;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {

    }

    public int inputColumns() {
        return word2Vec.getLayerSize();
    }

    public int totalOutcomes() {
        return numDimensions;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        documentIterator.reset();
    }

    public int batch() {
        return batch;
    }

    @Override
    public boolean hasNext() {
        currentDataSet=null;
        int idx = 0;
        int maxSamples = 16; // TODO get this from the pipeline manager
        INDArray labels = requireLabel?Nd4j.create(batch,totalOutcomes()):null;
        INDArray features = Nd4j.create(batch,inputColumns(),maxSamples);
        INDArray dates;
        INDArray masks = Nd4j.zeros(batch,maxSamples);
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        LocalDate today = LocalDate.of(2018,2,3);
        double[] datesArray = new double[batch];
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            String label = document.getSequenceLabel().getLabel();
            if(document.getSequenceLabels().size()<2) continue;
            LocalDate date = LocalDate.parse(document.getSequenceLabels().get(1).getLabel(), DateTimeFormatter.ISO_DATE);
            INDArray labelVec = labels==null||vectorizer==null?null:vectorizer.vectorFor(label);
            if (labelVec == null && requireLabel) continue;
            //Map<String, Integer> wordCounts = groupingBOWFunction.apply(document.getElements());

            List<String> sequence = document.getElements().stream().limit(Math.min(maxSamples,rand.nextInt(document.getElements().size())+1)).map(e->e.getLabel()).collect(Collectors.toList());
            totalWordsPerBatch.getAndAdd(sequence.size());

            if(sequence.size()==0) continue;
            INDArray featureVec = Nd4j.zeros(maxSamples,inputColumns());
            {
                List<Integer> indexes = new ArrayList<>();
                for (int i = 0; i < sequence.size(); i++) {
                    String l = sequence.get(i);
                    if (word2Vec.getVocab().containsWord(l)) {
                        indexes.add(word2Vec.getVocab().indexOf(l));
                    }
                }


                if (indexes.size() == 0) {
                    System.out.print("no indices...");
                    continue;
                }

                INDArray vec = Nd4j.pullRows(word2Vec.getLookupTable().getWeights(), 1, indexes.stream().mapToInt(i -> i).toArray());
                vec.diviColumnVector(vec.norm2(1));
                featureVec.get(NDArrayIndex.interval(0,vec.rows()),NDArrayIndex.all()).assign(vec);
                masks.get(NDArrayIndex.point(idx),NDArrayIndex.interval(0,vec.rows())).assign(1);
            }

            //featureVec = featureVec.mean(0);
            if(labels!=null)labels.putRow(idx,labelVec);
            double remainingLife = (((double)today.getYear()+((double)today.getMonthValue()-1)/12) - ((double)date.getYear()+((double)date.getMonthValue()-1)/12));
            double remainingLifeNorm = (remainingLife-10d)/10d;
            datesArray[idx] = remainingLifeNorm;
            features.get(NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.all()).assign(featureVec.transposei());
            idx++;
        }

        //System.out.println("Words found: "+wordsFoundPerBatch.get() + " / "+totalWordsPerBatch.get());

        if(idx>0) {
            //dates = Nd4j.create(datesArray).reshape(datesArray.length,1);
            if(idx < batch) {
                features = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all(),NDArrayIndex.all());
                masks = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                if(labels!=null)labels = labels.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
             //   dates = dates.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
            }
            if(labels!=null)labels.diviColumnVector(labels.norm2(1));
            INDArray[] allFeatures = labels!=null ? new INDArray[]{features,labels} : new INDArray[]{features};
            currentDataSet = new MultiDataSet(allFeatures,allFeatures,new INDArray[]{masks},new INDArray[]{masks});
        }
        return currentDataSet!=null;
    }

    @Override
    public MultiDataSet next() {
        return next(batch());
    }

    public static INDArray getPhraseVector(Word2Vec word2Vec, String phrase, double freq) {
        INDArray vec = word2Vec.getLookupTable().vector(phrase);
        if(vec==null) return null;
        return vec.mul(freq);
    }
}
