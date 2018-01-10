package elasticsearch;

import com.mongodb.MongoNamespace;
import com.mongodb.ReadPreference;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.binding.AsyncReadBinding;
import com.mongodb.binding.AsyncSingleConnectionReadBinding;
import com.mongodb.binding.ReadBinding;
import com.mongodb.connection.AsyncConnection;
import com.mongodb.connection.ServerDescription;
import com.mongodb.operation.ParallelCollectionScanOperation;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ehallmark on 8/14/17.
 */
public class IngestMongoIntoElasticSearch {
    static AtomicLong cnt = new AtomicLong(0);
    public static void main(String[] args) {
        // ingest assets (aka children)
        ingestByType(DataIngester.TYPE_NAME);
        DataIngester.close();
    }

    public static void deleteIndex() {
        try {
            MyClient.get().delete(new DeleteRequest(DataIngester.INDEX_NAME)).get();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void ingestByType(String type) {
        ingestByType(type, new Document());
    }

    public static void ingestByType(String type, Document query) {
        Consumer<Document> consumer = doc -> {
            String id = doc.getString("_id");
            DataIngester.ingestBulkFromMongoDB(type, id, addCountsToDoc(doc));
        };

        iterateOverCollection(consumer,query,type);
    }

    static SingleResultCallback<List<Document>> helper(AsyncBatchCursor<Document> cursor, Consumer<Document> consumer, String type) {
        return (docList, t2) -> {
            //System.out.println("Ingesting batch of : "+docList.size());
            docList.parallelStream().forEach(doc->{
                try {
                    consumer.accept(doc);
                } finally {
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Ingested: " + cnt.get());
                    }
                }
            });
            cursor.next(helper(cursor, consumer, type));
        };
    }

    public static void iterateOverCollection(Consumer<Document> consumer, Document query, String type) {
        String index = DataIngester.INDEX_NAME;
        MongoCollection<Document> collection = MongoDBClient.get().getDatabase(index).getCollection(type);
        AtomicLong total = new AtomicLong(0);
        collection.count(query, (count,t)->{
            total.set(count);
        });
        while(total.get()==0) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Total count of "+type+": "+total.get());
        FindIterable<Document> iterator = collection.find(query);

        iterator.batchSize(500).batchCursor((cursor,t)->{
            cursor.next(helper(cursor, consumer, type));
        });
        System.out.println("Total count of "+type+": "+cnt.get());
        while(cnt.get()<total.get()) {
            System.out.println("Waiting for mongo db. Remaining "+type+": "+(total.get()-cnt.get()));
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch(Exception e) {

            }
        }
        cnt.set(0);
    }

    private static Document addCountsToDoc(Document doc) {
        new ArrayList<>(doc.keySet()).forEach(e->{
            addCountsToDocHelper(e,doc.get(e), doc);
        });
        return doc;
    }

    private static void addCountsToDocHelper(String field, Object val, Document currentDoc) {
        if(val instanceof List || val instanceof Object[]) {
            int count;
            if(val instanceof Object[]) {
                count = ((Object[]) val).length;
            } else {
                count = ((List)val).size();
            }
            currentDoc.put(field + Constants.COUNT_SUFFIX, count);
        }
    }
}
