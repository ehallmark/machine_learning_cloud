package elasticsearch;

import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    private static BulkProcessor bulkProcessor = MyClient.getBulkProcessor();
    static final String INDEX_NAME = "ai_db";
    static final String TYPE_NAME = "patents_and_applications";
    private static MongoCollection<Document> mongoCollection = MongoDBClient.get().getDatabase(INDEX_NAME).getCollection(TYPE_NAME);
    private static AtomicLong mongoCount = new AtomicLong(0);

    public static synchronized void ingestBulk(String name, Map<String,Object> doc, boolean create) {
       // if(create) bulkProcessor.add(new IndexRequest(INDEX_NAME,TYPE_NAME, name).source(doc));
       // else bulkProcessor.add(new UpdateRequest(INDEX_NAME,TYPE_NAME, name).doc(doc));
        ingestMongo(name, null, doc, create);
    }

    public static synchronized void ingestBulkFromMongoDB(String name,  Document doc) {
        doc.remove("_id");
        doc.remove(Constants.ASSIGNMENTS); // RIGHT NOW ASSIGNMENTS ARE TOO EXPENSIVE
        bulkProcessor.add(new IndexRequest(INDEX_NAME,TYPE_NAME, name).source(doc));
    }

    static List<Document> insertBatch = new ArrayList<>();
    static List<WriteModel<Document>> updateBatch = new ArrayList<>();
    static final int batchSize = 5000;

    public static synchronized void ingestMongo(String id, Document query, Map<String,Object> doc, boolean create) {
        if(create) {
            doc.put("_id", id);
            insertBatch.add(new Document(doc));
        } else {
            Document updateDoc = new Document("$set",doc);
            query = query == null ? new Document("_id", id) : query.append("_id", id);
            WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
            updateBatch.add(model);
        }
        if(updateBatch.size()> batchSize) {
            updateBatch();
        }
        if(insertBatch.size() > batchSize) {
            insertBatch();
        }
    }

    public static synchronized void updateMongoByQuery(Document query, Map<String,Object> doc) {
        Document updateDoc = new Document("$set",doc);
        WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
        updateBatch.add(model);
        if(updateBatch.size()> batchSize) {
            updateBatch();
        }
        if(insertBatch.size() > batchSize) {
            insertBatch();
        }
    }

    public static synchronized void updateMongoArray(String id, String nestedName, String nestedId, Map<String,Object> doc) {
        Document updateDoc = new Document("$push",doc);
        Map<String,String> ne = new HashMap<>();
        ne.put("%ne",nestedId);
        Document query = new Document()
                .append("_id", id)
                .append(nestedName, ne);
        WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
        updateBatch.add(model);

        if(updateBatch.size()> batchSize) {
            updateBatch();
        }
        if(insertBatch.size() > batchSize) {
            insertBatch();
        }
    }

    private static void waitForMongo() {
        while(mongoCount.get() > 500) {
            System.out.println("Waiting for mongo to ingest batch...");
            try {
                TimeUnit.SECONDS.sleep(5);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void updateBatch() {
        waitForMongo();
        mongoCount.getAndIncrement();
        mongoCollection.bulkWrite(updateBatch, new BulkWriteOptions().ordered(false), (v,t)-> {
            mongoCount.getAndDecrement();
            if(t!=null) {
                System.out.println("Failed in update: "+t.getMessage());
            }
        });
        updateBatch = new ArrayList<>();
    }

    private static void insertBatch() {
        waitForMongo();
        mongoCount.getAndIncrement();
        mongoCollection.insertMany(insertBatch, new InsertManyOptions().ordered(false), (v, t) -> {
            mongoCount.getAndDecrement();
            if(t!=null) {
                System.out.println("Failed in insert: "+t.getMessage());
            }
        });
        insertBatch = new ArrayList<>();
    }

    public static synchronized void close() {
        if(updateBatch.size()>0) {
            updateBatch();
        }
        if(insertBatch.size()>0) {
            insertBatch();
        }
        while(mongoCount.get() > 0) {
            try {
                System.out.println("Waiting for "+mongoCount.get()+" assets");
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception e) {

            }
        }
        if(mongoCollection!=null) {
            MongoDBClient.close();
        }
        if(bulkProcessor!=null) {
            try {
                MyClient.closeBulkProcessor();
            } catch(Exception e) {

            }
        }
    }

    public static synchronized void ingestAssets(Map<String,Map<String,Object>> labelToTextMap, boolean create) {
        try {
            labelToTextMap.forEach((name,data)->{
                ingestBulk(name,data,create);
            });

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR UPDATING BATCH");
            //System.exit(1);
        } finally {
            labelToTextMap.clear();
        }
    }

    private static XContentBuilder buildJson(Map<String,Object> data) throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for(Map.Entry<String,Object> e : data.entrySet()) {
            builder = builder.field(e.getKey(),e.getValue());
        }
        builder = builder.endObject();
        return builder;
    }

    public static void ingestItems(Collection<Item> items, boolean create) {
        Map<String,Map<String,Object>> data = Collections.synchronizedMap(new HashMap<>(items.size()));
        items.parallelStream().forEach(item->{
            Map<String,Object> itemData = new HashMap<>();
            for(Map.Entry<String,Object> e : item.getDataMap().entrySet()) {
                itemData.put(e.getKey(), e.getValue());
            }
            data.put(item.getName(),itemData);
        });
        ingestAssets(data,create);
    }

}
