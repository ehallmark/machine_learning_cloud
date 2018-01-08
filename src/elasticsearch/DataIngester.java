package elasticsearch;

import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    private static final BulkProcessor bulkProcessor = MyClient.getBulkProcessor();
    public static final String OLD_INDEX_NAME = "ai_db";
    public static final String INDEX_NAME = "aidb2";
    public static final String TYPE_NAME = "p_a";
    private static MongoDatabase mongoDB = MongoDBClient.get().getDatabase(INDEX_NAME);
    private static AtomicLong mongoCount = new AtomicLong(0);
    private static FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

    public static void ingestBulk(String name, Map<String,Object> doc, boolean create) {
       // if(create) bulkProcessor.add(new IndexRequest(INDEX_NAME,TYPE_NAME, name).source(doc));
       // else bulkProcessor.add(new UpdateRequest(INDEX_NAME,TYPE_NAME, name).doc(doc));
        ingestMongo(name, doc, Collections.emptySet(), create);
    }

    public static void ingestBulkFromFiling(String filing, Map<String,Object> doc, boolean create) {
        Stream.of(filingToAssetMap.getApplicationDataMap().getOrDefault(filing,Collections.emptyList()),filingToAssetMap.getPatentDataMap().getOrDefault(filing, Collections.emptyList())).flatMap(list->list.stream()).forEach(name->{
            ingestBulk(name,doc,create);
        });
    }

    public static void ingestBulkFromMongoDB(String type, String name,  Document doc) {
        doc.remove("_id");
        IndexRequest request = new IndexRequest(INDEX_NAME,type, name);
        request = request.source(doc);
        synchronized (bulkProcessor) { bulkProcessor.add(request); }
    }

    public synchronized static void clearMongoDB() {
        mongoDB.drop((v,t)->{
            if(t==null) {
                System.out.println("Successfully cleared mongo.");
                mongoDB = MongoDBClient.get().getDatabase(INDEX_NAME);
            } else {
                System.out.println("Error clearing mongo: "+t.getMessage());
            }
        });
    }

    static final Map<String,List<Document>> insertBatchMap = Collections.synchronizedMap(new HashMap<>());
    static final Map<String,List<WriteModel<Document>>> updateBatchMap = Collections.synchronizedMap(new HashMap<>());

    private static final int batchSize = 10000;

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
        synchronized (updateBatchMap) {
            if (!updateBatchMap.containsKey(collection)) {
                updateBatchMap.put(collection, Collections.synchronizedList(new ArrayList<>()));
            }
        }
        updateBatchMap.get(collection).add(model);
        if(updateCounter.getAndIncrement() > batchSize) {
            updateCounter.set(0);
            updateBatch();
        }
    }

    public static synchronized void ingestMongo(String id, Map<String,Object> doc, Set<String> unset, boolean create) {
        if(create) {
            if(id!=null) {
                Document upsertDoc = new Document("$set",doc);
                Document upsertQuery = new Document("_id", id);
                WriteModel<Document> model = new UpdateOneModel<>(upsertQuery, upsertDoc, new UpdateOptions().upsert(true));
                addToUpdateMap(TYPE_NAME, model);
            }
        } else {
            Map<String,String> unsetMap = unset.isEmpty()?Collections.emptyMap():unset.stream().collect(Collectors.toMap(d->d,d->""));
            if (id != null) {
                Map<String,Object> updateDoc = new HashMap<>();
                if(doc.size()>0) {
                    updateDoc.put("$set",doc);
                }
                if(unsetMap.size()>0){
                    updateDoc.put("$unset", unsetMap);
                }
                if(!updateDoc.isEmpty()) {
                    Document updateQuery = new Document("_id", id);
                    WriteModel<Document> model = new UpdateOneModel<>(updateQuery, new Document(updateDoc));
                    addToUpdateMap(TYPE_NAME, model);
                }
            }
        }
    }

    public static void updateMongoByQuery(String collection, Document query, Map<String,Object> doc) {
        Document updateDoc = new Document("$set",doc);
        WriteModel<Document> model = new UpdateOneModel<>(query, updateDoc);
        addToUpdateMap(collection,model);
    }

    public static void updateMongoArray(String collection, Document query, String arrayName, Object value, String constraintKey, Object constraintValue) {
        Document updateDoc = new Document("$push",new Document(arrayName,value));
        Document not = new Document("$not", new Document("$elemMatch", new Document(constraintKey, new Document("$eq", constraintValue))));
        query = query.append(arrayName, not);
        WriteModel<Document> model = new UpdateManyModel<>(query, updateDoc);
        addToUpdateMap(collection,model);
    }

    public static void updateMongoSet(String collection, Document query, String arrayName, Object value) {
        Document updateDoc = new Document("$addToSet",new Document(arrayName,value));
        WriteModel<Document> model = new UpdateManyModel<>(query, updateDoc);
        addToUpdateMap(collection,model);
    }

    private static void waitForMongo() {
        while(mongoCount.get() >= 300) {
            System.out.println("Waiting for mongo to ingest batch...");
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void updateBatch() {
        synchronized (DataIngester.class) {
            waitForMongo();
            updateBatchMap.forEach((collection, updateBatch) -> {
                if (updateBatch.size() > 0) {
                    mongoCount.getAndIncrement();
                    mongoDB.getCollection(collection).bulkWrite(new ArrayList<>(updateBatch), new BulkWriteOptions().ordered(false), (v, t) -> {
                        mongoCount.getAndDecrement();
                        if (t != null) {
                            System.out.println("Failed in update: " + t.getMessage());
                        }
                    });
                }
                updateBatch.clear();
            });
        }
    }

    private static void insertBatch() {
        synchronized (DataIngester.class) {
            waitForMongo();
            insertBatchMap.forEach((collection, insertBatch) -> {
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
            });
        }
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
                TimeUnit.SECONDS.sleep(1);
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

    public static void ingestItem(Item item, Set<String> unset) {
        Map<String,Object> itemData = item.getDataMap();
        String name = item.getName();
        if(itemData.size()>0 || unset.size()>0) {
            ingestMongo(name,itemData,unset,false);
        }
    }

}
