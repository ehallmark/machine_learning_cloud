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

    static SearchRequestBuilder getRequestBuilder(TransportClient client) {
        SearchRequestBuilder requestBuilder = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .addStoredField("_parent")
                .addStoredField("_source")
                .setFetchSource(new String[]{Constants.INVENTION_TITLE,Constants.ABSTRACT,Constants.DOC_TYPE,"_parent"}, new String[]{})
                .setFrom(0)
                .setSize(10000)
                .setScroll(new TimeValue(60000))
                .setExplain(false);
        return requestBuilder;
    }

    public static SequenceIterator<VocabWord> PatentParagraphSequenceIterator(int numEpochs) throws SQLException {
        ArrayBlockingQueue<Sequence<VocabWord>> queue = new ArrayBlockingQueue<Sequence<VocabWord>>(20000);
        TransportClient client = MyClient.get();

        Function<SearchHit,Item> transformer = hit-> {
            String id = hit.getId();
            Map<String,Object> source = hit.getSource();
            Object inventionTitle = source.get(Constants.INVENTION_TITLE);
            Object abstractText = source.get(Constants.ABSTRACT);
            Object docType = source.get(Constants.DOC_TYPE);
            String assigneeName;
            if(docType.equals("patents")) {
                assigneeName = patentToAssigneeMap.get(id);
            } else {
                assigneeName = appToAssigneeMap.get(id);
            }
            Object filing = source.getOrDefault("_parent", hit.getField("_parent"));
            if(filing!=null) {

                DuplicatableSequence<VocabWord> sequence = new DuplicatableSequence<>();
                sequence.setSequenceLabel(new VocabWord(1.0,filing.toString()));

                if(inventionTitle!=null) {
                    sequence.addElements(getSequence(inventionTitle.toString()));
                }

                if(abstractText!=null) {
                    sequence.addElements(getSequence(abstractText.toString()));
                }

                if(sequence.size() > 0) {
                    // add to queue
                    while(!queue.offer(sequence)) {
                        try {
                            System.out.println("Waiting for offer...");
                            TimeUnit.MILLISECONDS.sleep(200);
                        } catch(Exception e) {

                        }
                    }
                    if (assigneeName != null) {
                        Sequence<VocabWord> assigneeSequence = sequence.dup();
                        assigneeSequence.setSequenceLabel(new VocabWord(1.0, assigneeName));
                        while(!queue.offer(assigneeSequence)) {
                            System.out.println("Waiting for offer...");
                            try {
                                TimeUnit.MILLISECONDS.sleep(200);
                            } catch(Exception e) {

                            }
                        }
                    }
                }
            }

            return null;
        };

        RecursiveAction iterThread = new RecursiveAction() {
            @Override
            protected void compute() {
                DataSearcher.iterateOverSearchResults(getRequestBuilder(client).get(),transformer,-1,false);
            }
        };

        iterThread.fork();

        return new SequenceIterator<VocabWord>() {
            RecursiveAction iter = iterThread;
            @Override
            public boolean hasMoreSequences() {
                return !(iter.isDone()||iter.isCancelled());
            }

            @Override
            public Sequence<VocabWord> nextSequence() {
                while(queue.isEmpty()) {
                    // sleep
                    System.out.println("Waiting for next seq...");
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch(Exception e) {

                    }
                }
                return queue.poll();
            }

            @Override
            public void reset() {
                if(!iter.isDone()) {
                    try {
                        iter.cancel(true);
                    } catch(Exception e) {

                    }
                }
                iter = getNewIter(getRequestBuilder(client).get(),transformer);
                iter.fork();
            }
        };
    }

    static RecursiveAction getNewIter(SearchResponse searchResponse, Function<SearchHit,Item> transformer) {
        return new RecursiveAction() {
            @Override
            protected void compute() {
                DataSearcher.iterateOverSearchResults(searchResponse,transformer,-1,false);
            }
        };
    }

}
