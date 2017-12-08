package models.similarity_models.cpc2vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.Setter;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class CPC2VecIterator implements SequenceIterator<VocabWord> {
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    @Setter
    private Function<Void,Void> afterEpochFunction;
    private List<String> assets;
    private Iterator<String> iterator;
    protected Map<String,Collection<CPC>> cpcMap;
    public CPC2VecIterator(List<String> assets, int numEpochs, Map<String,Collection<CPC>> cpcMap) {
        this(assets,numEpochs,cpcMap,null);
    }

    public CPC2VecIterator(List<String> assets, int numEpochs,Map<String,Collection<CPC>> cpcMap, Function<Void,Void> afterEpochFunction) {
        this.numEpochs=numEpochs;
        this.cpcMap=cpcMap;
        this.assets=new ArrayList<>(assets);
        this.iterator=assets.iterator();
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
    public Sequence<VocabWord> nextSequence() {
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
        if(task!=null && !task.isDone()) return;
        queue.clear();
        final boolean singleEpoch = vocabPass;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                int finalNumEpochs = singleEpoch ? 1 : numEpochs;
                System.out.println("Running "+finalNumEpochs+" epochs: "+finalNumEpochs);
                for(int i = 0; i < finalNumEpochs; i++) {
                    while(iterator.hasNext()) {
                        String asset = iterator.next();
                        List<VocabWord> cpcs = cpcMap.getOrDefault(asset,Collections.emptyList()).stream()
                                .map(cpc->new VocabWord(1d,cpc.getName()))
                                .collect(Collectors.toCollection(ArrayList::new));
                        Collections.shuffle(cpcs, new Random());
                        Sequence<VocabWord> sequence = new Sequence<>(cpcs);
                        try {
                            queue.put(sequence);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Collections.shuffle(assets, new Random());
                    iterator = assets.iterator();
                    while(queue.size()>0) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch(Exception e) {
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
    public boolean hasMoreSequences() {
        return hasNextDocument();
    }

}
