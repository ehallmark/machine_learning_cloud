package elasticsearch;

import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.elasticsearch.action.delete.DeleteRequest;
import seeding.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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
            doc.putIfAbsent(Constants.NAME,id);
            DataIngester.ingestBulkFromMongoDB(type, addCountsToDoc(doc));
        };

        iterateOverCollection(consumer,query,type);
    }

    private static SingleResultCallback<List<Document>> helper(AsyncBatchCursor<Document> cursor, Consumer<Document> consumer) {
        return (docList, t2) -> {
            //System.out.println("Ingesting batch of : "+docList.size());
            docList.parallelStream().forEach(doc->{
                try {
                    consumer.accept(doc);
                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Ingested: " + cnt.get());
                    }
                }
            });
            cursor.next(helper(cursor, consumer));
        };
    }

    public static void iterateOverCollection(Consumer<Document> consumer, Document query, String type, String... fields) {
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
        if(fields!=null&&fields.length>0) {
            iterator = iterator.projection(Projections.include(fields));
        }

        iterator.batchSize(500).batchCursor((cursor,t)->{
            cursor.next(helper(cursor, consumer));
        });
        System.out.println("Total count of "+type+": "+cnt.get());
        while(cnt.get()<total.get()) {
            System.out.println("Waiting for mongo db. Remaining "+type+": "+(total.get()-cnt.get()));
            try {
                TimeUnit.SECONDS.sleep(20);
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
