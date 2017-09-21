package models.dl4j_neural_nets.iterators.sequences;

import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.DataIngester;
import elasticsearch.MongoDBClient;
import elasticsearch.MyClient;
import models.dl4j_neural_nets.tools.DuplicatableSequence;
import org.bson.Document;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
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

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    static SingleResultCallback<List<Document>> helper(AsyncBatchCursor<Document> cursor, AtomicLong cnt) {
        return (docList, t2) -> {
            //System.out.println("Ingesting batch of : "+docList.size());
            docList.parallelStream().forEach(doc->{
                try {
                    String id = doc.getString("_id");
                    String inventionTitle = doc.getString(Constants.INVENTION_TITLE);
                    String abstractText = doc.getString(Constants.ABSTRACT);
                    String docType = doc.getString(Constants.DOC_TYPE);
                    String assigneeName;
                    if(docType.equals("patents")) {
                        assigneeName = patentToAssigneeMap.get(id);
                    } else {
                        assigneeName = appToAssigneeMap.get(id);
                    }
                    String filing = doc.getString("_parent");
                    if(filing!=null) {

                        DuplicatableSequence<VocabWord> sequence = new DuplicatableSequence<>();
                        sequence.setSequenceLabel(new VocabWord(1.0,filing));

                        if(inventionTitle!=null) {
                            sequence.addElements(getSequence(inventionTitle));
                        }

                        if(abstractText!=null) {
                            sequence.addElements(getSequence(abstractText));
                        }

                        if(sequence.size() > 0) {
                            // add to queue
                            // TODO
                            if (assigneeName != null) {
                                Sequence<VocabWord> duplicatableSequence = sequence.dup();
                                duplicatableSequence.setSequenceLabel(new VocabWord(1.0, assigneeName));

                            }
                        }
                    }
                } finally {
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Ingested: " + cnt.get());
                    }
                }
            });
            cursor.next(helper(cursor, cnt));
        };
    }

    public static SequenceIterator<VocabWord> PatentParagraphSequenceIterator(int numEpochs) throws SQLException {
        MongoClient client = MongoDBClient.get();

        MongoCollection<Document> collection = client.getDatabase(DataIngester.INDEX_NAME).getCollection(DataIngester.TYPE_NAME);

        AtomicLong total = new AtomicLong(0);
        AtomicLong cnt = new AtomicLong(0);
        {
            // get counts
            collection.count(new Document(), (count,t)->{
                total.set(count);
            });
            while(total.get()==0) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        FindIterable<Document> iterator = collection.find(new Document());

        iterator.batchSize(1000).batchCursor((cursor,t)->{
            cursor.next(helper(cursor,cnt));
        });

        ArrayBlockingQueue<Sequence<VocabWord>> queue = new ArrayBlockingQueue<Sequence<VocabWord>>(10000);

        return new SequenceIterator<VocabWord>() {
            @Override
            public boolean hasMoreSequences() {
                return cnt.get()<total.get();
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
                System.out.println("Reset called: Not sure what to do here....");
            }
        };
    }

}
