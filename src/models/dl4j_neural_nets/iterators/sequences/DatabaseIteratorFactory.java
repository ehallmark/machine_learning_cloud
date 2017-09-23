package models.dl4j_neural_nets.iterators.sequences;

import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import elasticsearch.MongoDBClient;
import elasticsearch.MyClient;
import models.dl4j_neural_nets.tools.DuplicatableSequence;
import org.bson.Document;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import seeding.Constants;
import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.AssigneeTrimmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/19/16.
 */
public class DatabaseIteratorFactory {
    static final AssetToAssigneeMap assetToAssigneeMap = new AssetToAssigneeMap();
    static final Map<String,String> patentToAssigneeMap = assetToAssigneeMap.getPatentDataMap();
    static final Map<String,String> appToAssigneeMap = assetToAssigneeMap.getApplicationDataMap();

    static Collection<VocabWord> getSequence(String text) {
        return Stream.of(new CommonPreprocessor().preProcess(text).split("\\s+")).filter(w->w!=null&&w.length()>0).map(word->new VocabWord(1.0,word)).collect(Collectors.toList());
    }

    static SingleResultCallback<List<Document>> helper(AsyncBatchCursor<Document> cursor, AtomicLong cnt, Queue<Sequence<VocabWord>> queue) {
        return (docList, t2) -> {
            //System.out.println("Ingesting batch of : "+docList.size());
            docList.parallelStream().forEach(doc->{
                try {

                } finally {
                    if (cnt.getAndIncrement() % 100000 == 99999) {
                        System.out.println("Seen: " + cnt.get());
                    }
                }
            });
            cursor.next(helper(cursor, cnt, queue));
        };
    }


    public static SequenceIterator<VocabWord> PatentParagraphSequenceIterator(int numEpochs) throws SQLException {
        ArrayBlockingQueue<Sequence<VocabWord>> queue = new ArrayBlockingQueue<Sequence<VocabWord>>(50000);
        TransportClient client = MyClient.get();

        Function<SearchHit,Item> transformer = hit-> {
            String id = hit.getId();
            Map<String,Object> source = hit.getSource();
            Object abstractText = source.get(Constants.ABSTRACT);
            Object docType = source.get(Constants.DOC_TYPE);
            String assigneeName;
            if(docType==null||docType.equals("patents")) {
                assigneeName = patentToAssigneeMap.get(id);
            } else {
                assigneeName = appToAssigneeMap.get(id);
            }
            Object filing = hit.getField("_parent").getValue();
            if(filing!=null) {

                System.out.println("Filing: "+filing.toString());

                DuplicatableSequence<VocabWord> sequence = new DuplicatableSequence<>();
                sequence.setSequenceLabel(new VocabWord(1.0,filing.toString()));


                if(abstractText!=null) {
                    sequence.addElements(getSequence(abstractText.toString()));
                }

                if(sequence.size() > 0) {
                    // add to queue
                    try {
                        queue.put(sequence);
                        if (assigneeName != null) {
                            Sequence<VocabWord> assigneeSequence = sequence.dup();
                            assigneeSequence.setSequenceLabel(new VocabWord(1.0, assigneeName));
                            queue.put(assigneeSequence);
                        }
                    } catch(Exception e) {

                    }
                }
            }

            return null;
        };


        SequenceIterator<VocabWord> iterator = new SequenceIterator<VocabWord>() {
            boolean firstRunThrough = true;
            RecursiveAction iter;
            @Override
            public boolean hasMoreSequences() {
                boolean hasMore = queue.size()>0 || !(iter.isDone()||iter.isCancelled());
                if(!hasMore) {
                    System.out.println("ITER COMPLETED NORMALLY? "+iter.isCompletedNormally());
                }
                return hasMore;
            }

            @Override
            public Sequence<VocabWord> nextSequence() {
                try {
                    return queue.take();
                } catch(Exception e) {
                    return null;
                }
            }

            @Override
            public void reset() {

                if(iter != null && iter.isCompletedAbnormally()) {
                    throw new RuntimeException("Error while iterating: "+iter.getException());
                }
                if(iter != null && !iter.isDone()) {
                    try {
                        System.out.println("CANCELLING ITER!!!");
                        iter.cancel(true);
                    } catch(Exception e) {

                    }
                }
                queue.clear();
                iter = getNewIter(client,transformer, firstRunThrough, numEpochs);
                iter.fork();
                try {
                    TimeUnit.MILLISECONDS.sleep(2000);
                } catch(Exception e) {

                }
                firstRunThrough=false;
            }
        };

        //iterator.reset();
        return iterator;
    }

    static SearchRequestBuilder getRequestBuilder(TransportClient client) {
        SearchRequestBuilder requestBuilder = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .addStoredField("_parent")
                .addStoredField("_source")
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.DOC_TYPE,"_parent"}, new String[]{})
                .setFrom(0)
                .setSize(10000)
                .setScroll(new TimeValue(60000))
                .setExplain(false);
        return requestBuilder;
    }

    static RecursiveAction getNewIter(TransportClient client, Function<SearchHit,Item> transformer, boolean firstRunThrough, int numEpochs) {
        return new RecursiveAction() {
            @Override
            protected void compute() {
                System.out.println("GETTING NEW ITERATOR...");
                if(firstRunThrough) {
                    System.out.print("FIRST RUN THROUGH");
                    DataSearcher.iterateOverSearchResults(getRequestBuilder(client).get(), transformer, -1, false);
                } else {
                    for(int i = 0; i < numEpochs; i++) {
                        System.out.println("STARTING EPOCH: "+(i+1));
                        DataSearcher.iterateOverSearchResults(getRequestBuilder(client).get(), transformer, -1, false);
                    }
                }
            }
        };
    }

}
