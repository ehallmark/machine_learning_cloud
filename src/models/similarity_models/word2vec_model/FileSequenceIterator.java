package models.similarity_models.word2vec_model;

import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.stages.Stage;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class FileSequenceIterator implements SentenceIterator {
    private ArrayBlockingQueue<String> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    private FileTextDataSetIterator iterator;
    public FileSequenceIterator(FileTextDataSetIterator iterator, int numEpochs) {
        this(iterator,numEpochs,null);
    }

    public FileSequenceIterator(FileTextDataSetIterator iterator, int numEpochs, Function<Void,Void> afterEpochFunction) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.queue = new ArrayBlockingQueue<>(5000);
        this.vocabPass=true;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }


    public boolean hasNextDocument() {
        return queue.size()>0 || !task.isDone();
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor sentencePreProcessor) {

    }

    @Override
    public String nextSentence() {
        while (!task.isDone() && queue.isEmpty()) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return queue.poll();
    }


    @Override
    public void reset() {
        if(task!=null) task.join();
        queue.clear();
        final boolean singleEpoch = vocabPass;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                int finalNumEpochs = singleEpoch ? 1 : numEpochs;
                System.out.println("Running "+finalNumEpochs+" epochs: "+finalNumEpochs);
                for(int i = 0; i < finalNumEpochs; i++) {
                    while(iterator.hasNext()) {
                        LabelledDocument document = iterator.next();
                        try {
                            queue.put(document.getContent());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    System.out.println("Finished epoch: "+(i+1));
                    // Evaluate model
                    if(afterEpochFunction!=null)afterEpochFunction.apply(null);
                }
            }
        };
        task.fork();
        vocabPass=false;
    }

    @Override
    public void finish() {
        iterator.shutdown();
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
    }

}
