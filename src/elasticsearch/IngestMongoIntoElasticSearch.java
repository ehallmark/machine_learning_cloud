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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Created by ehallmark on 8/14/17.
 */
public class IngestMongoIntoElasticSearch {
    static AtomicLong cnt = new AtomicLong(0);
    public static void main(String[] args) {
        final boolean debug = false;
        String index = DataIngester.INDEX_NAME;
        String type = DataIngester.TYPE_NAME;
        MongoCollection<Document> collection = MongoDBClient.get().getDatabase(index).getCollection(type);
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

        iterator.batchSize(1000).batchCursor((cursor,t)->{
            cursor.next(helper(cursor));
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

    static SingleResultCallback<List<Document>> helper(AsyncBatchCursor<Document> cursor) {
        return (docList, t2) -> {
            //System.out.println("Ingesting batch of : "+docList.size());
            docList.iterator().forEachRemaining(doc->{
                try {
                    DataIngester.ingestBulkFromMongoDB(doc.getString("_id"), doc);
                } finally {
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Ingested: " + cnt.get());
                    }
                }
            });
            cursor.next(helper(cursor));
        };
    }
}
