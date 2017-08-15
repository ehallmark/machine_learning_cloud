package elasticsearch;

import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import org.bson.Document;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 8/14/17.
 */
public class IngestMongoIntoElasticSearch {
    public static void main(String[] args) {
        final boolean debug = false;
        String index = DataIngester.INDEX_NAME;
        String type = DataIngester.TYPE_NAME;
        MongoCollection<Document> collection = MongoDBClient.get().getDatabase(index).getCollection(type);
        AtomicLong cnt = new AtomicLong(0);
        AtomicLong total = new AtomicLong(0);
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
        System.out.println("Total count: "+total.get());
        FindIterable<Document> iterator = collection.find(new Document());
        iterator.batchSize(100).batchCursor((cursor,t)->{
            AtomicReference<Throwable> shouldStop = new AtomicReference<>(null);
            while(shouldStop.get()==null) {
                cursor.next((docList, t2) -> {
                    if (docList == null || docList.isEmpty()) {
                        docList.stream().forEach(doc -> {
                            try {
                                if (debug) {
                                    System.out.println("Ingesting: " + doc.getString("_id"));
                                }
                                DataIngester.ingestBulkFromMongoDB(doc.getString("_id"), doc);
                            } finally {
                                if (cnt.getAndIncrement() % 10000 == 9999) {
                                    System.out.println("Ingested: " + cnt.get());
                                }
                            }
                        });
                    }
                    shouldStop.set(t2);
                });
            }
        });
        System.out.println("Total count: "+cnt.get());
        while(cnt.get()<total.get()) {
            System.out.println("Waiting for mongo db. Remaining: "+(total.get()-cnt.get()));
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch(Exception e) {

            }
        }
        DataIngester.close();
    }
}
