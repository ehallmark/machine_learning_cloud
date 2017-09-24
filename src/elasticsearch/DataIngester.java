package elasticsearch;

import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
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
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    private static BulkProcessor bulkProcessor = MyClient.getBulkProcessor();
    public static final String INDEX_NAME = "ai_db";
    public static final String TYPE_NAME = "patents_and_applications";
    public static final String PARENT_TYPE_NAME = "filings";
    private static MongoDatabase mongoDB = MongoDBClient.get().getDatabase(INDEX_NAME);
    private static AtomicLong mongoCount = new AtomicLong(0);
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

    public static void ingestBulk(String name, String parent, Map<String,Object> doc, boolean create) {
       // if(create) bulkProcessor.add(new IndexRequest(INDEX_NAME,TYPE_NAME, name).source(doc));
       // else bulkProcessor.add(new UpdateRequest(INDEX_NAME,TYPE_NAME, name).doc(doc));
        ingestMongo(name, parent, null, doc, create);
    }

    public static void ingestBulkFromMongoDB(String type, String name,  Document doc) {
        doc.remove("_id");
        Object parent = doc.get("_parent");
        IndexRequest request = new IndexRequest(INDEX_NAME,type, name);
        if(parent!=null) {
            request = request.parent(parent.toString());
            doc.remove("_parent");
        }
        request = request.source(doc);
        synchronized (bulkProcessor) { bulkProcessor.add(request); }
    }

    static Map<String,List<Document>> insertBatchMap = Collections.synchronizedMap(new HashMap<>());
    static Map<String,List<WriteModel<Document>>> updateBatchMap = Collections.synchronizedMap(new HashMap<>());

    static final int batchSize = 10000;

    private static final AtomicInteger insertCounter = new AtomicInteger(0);
    private static final AtomicInteger updateCounter = new AtomicInteger(0);

   /* private static void addToInsertMap(String collection, Document doc) {
        if(!insertBatchMap.containsKey(collection)) {
            insertBatchMap.put(collection, Collections.synchronizedList(new ArrayList<>()));
        }
        insertBatchMap.get(collection).add(doc);
        if(insertCounter.getAndIncrement() > batchSize) {
            insertCounter.set(0);
            insertBatch();
        }
    } */

    private static void addToUpdateMap(String collection, WriteModel<Document> model) {
        if(!updateBatchMap.containsKey(collection)) {
            updateBatchMap.put(collection, Collections.synchronizedList(new ArrayList<>()));
        }
        updateBatchMap.get(collection).add(model);
        if(updateCounter.getAndIncrement() > batchSize) {
            updateCounter.set(0);
            updateBatch();
        }
    }
    
    public static void ingestMongo(String id, String parent, Document query, Map<String,Object> doc, boolean create) {
        Collection<String> filingAttributes = Constants.FILING_ATTRIBUTES_SET;
        Map<String,Object> assetDoc = new HashMap<>();
        Map<String,Object> filingDoc = new HashMap<>();
        doc.forEach((key,val)->{
            if(filingAttributes.contains(key)||(key.contains(".")&&filingAttributes.contains(key.substring(0,key.indexOf("."))))) {
                filingDoc.put(key,val);
            } else {
                assetDoc.put(key,val);
            }
        });
        if(create) {
            assetDoc.put("_parent", parent);
            filingDoc.put("_id", parent);
            if(id!=null) {
                Document upsertDoc = new Document("$set",assetDoc);
                Document upsertQuery = new Document("_id", id);
                WriteModel<Document> model = new UpdateOneModel<>(upsertQuery, upsertDoc, new UpdateOptions().upsert(true));
                addToUpdateMap(TYPE_NAME, model);
            }
            // upsert
            Document upsertParentDoc = new Document("$set",filingDoc);
            Document upsertParentQuery = new Document("_id", parent);
            WriteModel<Document> model = new UpdateOneModel<>(upsertParentQuery, upsertParentDoc, new UpdateOptions().upsert(true));
            addToUpdateMap(PARENT_TYPE_NAME, model);

        } else {
            if (id != null && assetDoc.size() > 0) {
                Document updateDoc = new Document("$set", assetDoc);
                Document updateQuery = query == null ? new Document("_id", id) : query.append("_id", id);
                WriteModel<Document> model = new UpdateOneModel<>(updateQuery, updateDoc);
                addToUpdateMap(TYPE_NAME, model);
            }
            if(parent!=null && filingDoc.size() > 0) {
                Document updateParentDoc = new Document("$set",filingDoc);
                Document updateParentQuery = new Document("_id", parent);
                WriteModel<Document> model = new UpdateOneModel<>(updateParentQuery, updateParentDoc);
                addToUpdateMap(PARENT_TYPE_NAME, model);
            }
        }
    }

    public static void updateMongoByQuery(String collection, Document query, Map<String,Object> doc) {
        Document updateDoc = new Document("$set",doc);
        WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
        addToUpdateMap(collection,model);
    }

    public static void updateMongoArray(String collection, Document query, String arrayName, String value) {
        Document updateDoc = new Document("$push",new Document(arrayName,value));
        Map<String,String> ne = new HashMap<>();
        ne.put("$ne",value);
        query = query.append(arrayName, ne);
        WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
        addToUpdateMap(collection,model);
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
        updateBatchMap.forEach((collection,updateBatch)->{
            synchronized (updateBatch) {
                if (updateBatch.size() > 0) {
                    mongoCount.getAndIncrement();
                    mongoDB.getCollection(collection).bulkWrite(new ArrayList<>(updateBatch), new BulkWriteOptions().ordered(false), (v, t) -> {
                        mongoCount.getAndDecrement();
                        if (t != null) {
                            System.out.println("Failed in update: " + t.getMessage());
                        }
                    });
                }
            }
            updateBatch.clear();
        });
    }

    private static void insertBatch() {
        waitForMongo();
        insertBatchMap.forEach((collection,insertBatch)->{
            synchronized (insertBatch) {
                if (insertBatch.size() > 0) {
                    mongoCount.getAndIncrement();
                    mongoDB.getCollection(collection).insertMany(new ArrayList<>(insertBatch), new InsertManyOptions().ordered(false), (v, t) -> {
                        mongoCount.getAndDecrement();
                        if (t != null) {
                            System.out.println("Failed in insert: " + t.getMessage());
                        }
                    });
                    insertBatch.clear();
                }
            }
        });
    }

    public static void finishCurrentMongoBatch() {
        if(updateCounter.get()>0) {
            updateBatch();
        }
        if(insertCounter.get()>0) {
            insertBatch();
        }
        while(mongoCount.get() > 0) {
            try {
                System.out.println("Waiting for "+mongoCount.get()+" assets");
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception e) {

            }
        }
    }

    public static synchronized void close() {
        finishCurrentMongoBatch();

        if(mongoDB!=null) {
            MongoDBClient.close();
        }
        if(bulkProcessor!=null) {
            try {
                MyClient.closeBulkProcessor();
            } catch(Exception e) {

            }
        }
    }

    public static void updateAssets(Map<String,Map<String,Object>> labelToTextMap) {
        try {
            labelToTextMap.entrySet().parallelStream().forEach(e->{
                String name = e.getKey();
                Map<String,Object> data = e.getValue();
                String filing = assetToFilingMap.getApplicationDataMap().getOrDefault(name,assetToFilingMap.getPatentDataMap().get(name));
                if(filing == null) return;
                ingestBulk(name,filing,data,false);
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

    public static void updateItems(Collection<Item> items) {
        Map<String,Map<String,Object>> data = Collections.synchronizedMap(new HashMap<>(items.size()));
        items.parallelStream().forEach(item->{
            Map<String,Object> itemData = new HashMap<>();
            for(Map.Entry<String,Object> e : item.getDataMap().entrySet()) {
                itemData.put(e.getKey(), e.getValue());
            }
            if(itemData.size()>0) {
                data.put(item.getName(), itemData);
            }
        });
        updateAssets(data);
    }

}
