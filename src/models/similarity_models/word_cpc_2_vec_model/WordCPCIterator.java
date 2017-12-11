package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import seeding.Database;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPCIterator implements LabelAwareIterator {
    private ArrayBlockingQueue<LabelledDocument> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    private FileTextDataSetIterator iterator;
    private Map<String,Collection<CPC>> cpcMap;
    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap) {
        this(iterator,numEpochs,cpcMap,null);
    }

    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, Function<Void,Void> afterEpochFunction) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.cpcMap=cpcMap;
        this.queue = new ArrayBlockingQueue<>(5000);
        this.vocabPass=true;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }

    public boolean hasNextDocument() {
        if (task == null) {
            synchronized (this) {
                if (task == null) {
                    reset();
                    long maxTime = 60 * 3 * 1000;
                    long t = 0;
                    while(queue.size()==0) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch(Exception e) {

                        }
                        t+=50;
                        if(t>maxTime) {
                            throw new RuntimeException("Waited over 3 minutes for next document...");
                        }
                    }
                }
            }
        }
        return queue.size()>0 || !task.isDone();
    }

    @Override
    public LabelledDocument nextDocument() {
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
    public synchronized void reset() {
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
                        LabelledDocument document = iterator.next();
                        if(document.getLabels()!=null&&document.getLabels().isEmpty()) continue;
                        List<String> cpcLabels = document.getLabels().stream().flatMap(asset->cpcMap.getOrDefault(cpcMap.get(asset), Collections.emptyList()).stream()).map(cpc->cpc.getName()).collect(Collectors.toList());
                        List<String> assigneeLabels = document.getLabels().stream().map(asset-> Database.assigneeFor(asset)).filter(assignee->assignee!=null).collect(Collectors.toList());
                        document.setLabels(Stream.of(cpcLabels,assigneeLabels).flatMap(list->list.stream()).collect(Collectors.toList()));
                        if(document.getLabels().isEmpty()) continue;
                        try {
                            queue.put(document);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    iterator.reset();
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
    public LabelsSource getLabelsSource() {
        return null;
    }

    @Override
    public void shutdown() {
        iterator.shutdown();
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
    }

    @Override
    public LabelledDocument next() {
        return nextDocument();
    }


}
