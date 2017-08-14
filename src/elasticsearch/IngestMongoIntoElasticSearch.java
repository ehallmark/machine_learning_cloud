package elasticsearch;

import com.mongodb.async.client.MongoCollection;
import org.bson.Document;

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
        collection.find().forEach(doc->{
            if(debug) {
                System.out.println("Ingesting: "+doc.getString("_id"));
            }
            DataIngester.ingestBulkFromMongoDB(doc.getString("_id"),doc);
        }, (v,t)->{
            if(t!=null) {
                System.out.println("Error from mongo: "+t.getMessage());
            }
            if(cnt.getAndIncrement() % 10000==9999) {
                System.out.println("Ingested: "+cnt.get());
            }
        });
        DataIngester.close();
    }
}
