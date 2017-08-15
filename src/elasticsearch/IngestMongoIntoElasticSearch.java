package elasticsearch;

import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoCollection;
import org.bson.Document;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 8/14/17.
 */
public class IngestMongoIntoElasticSearch {
    public static void main(String[] args) {
        final boolean debug = true;
        String index = DataIngester.INDEX_NAME;
        String type = DataIngester.TYPE_NAME;
        MongoCollection<Document> collection = MongoDBClient.get().getDatabase(index).getCollection(type);
        AtomicLong cnt = new AtomicLong(0);
        AtomicLong keepTrack = new AtomicLong(0);
        FindIterable<Document> iterator = collection.find(new Document());
        iterator.forEach(doc->{
            keepTrack.incrementAndGet();
            if(debug) {
                System.out.println("Ingesting: "+doc.getString("_id"));
            }
            DataIngester.ingestBulkFromMongoDB(doc.getString("_id"),doc);
        }, (v,t)->{
            keepTrack.getAndDecrement();
            if(t!=null) {
                System.out.println("Error from mongo: "+t.getMessage());
            }
            if(cnt.getAndIncrement() % 10000==9999) {
                System.out.println("Ingested: "+cnt.get());
            }
        });
        System.out.println("Total count: "+cnt.get());
        while(keepTrack.get()>0) {
            System.out.println("Waiting for mongo db. Remaining: "+keepTrack.get());
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch(Exception e) {

            }
        }
        DataIngester.close();
    }
}
