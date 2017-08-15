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
        iterator.forEach(doc->{
            if(debug) {
                System.out.println("Ingesting: "+doc.getString("_id"));
            }
            DataIngester.ingestBulkFromMongoDB(doc.getString("_id"),doc);
            if(cnt.getAndIncrement() % 10000==9999) {
                System.out.println("Ingested: "+cnt.get());
            }
        }, (v,t)->{
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch(Exception e) {

            }
            if(t!=null) {
                System.out.println("Error from mongo: "+t.getMessage());
            }
        });
        System.out.println("Total count: "+cnt.get());
        while(cnt.get()>0) {
            System.out.println("Waiting for mongo db. Remaining: "+keepTrack.get());
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch(Exception e) {

            }
        }
        DataIngester.close();
    }
}
