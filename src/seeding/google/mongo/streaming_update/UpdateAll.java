package seeding.google.mongo.streaming_update;

import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import elasticsearch.MongoDBClient;
import org.bson.Document;
import seeding.google.mongo.ingest.IngestJsonHelper;
import seeding.google.mongo.ingest.IngestPatents;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class UpdateAll {

    public static void main(String[] args) {
        List<StreamableUpdater> updaters = Arrays.asList(

        );
        MongoCollection collection = MongoDBClient.get().getDatabase(IngestPatents.INDEX_NAME).getCollection(IngestPatents.TYPE_NAME);
        AtomicInteger counter = new AtomicInteger(0);
        final String[] fields = updaters.stream().flatMap(updater->updater.getFields().stream()).distinct().toArray(size->new String[size]);
        Consumer<Document> consumer = doc->{
            updaters.forEach(updater->{
                updater.getConsumer().accept(doc);
            });
            Map<String,Object> set = Collections.synchronizedMap(new HashMap<>());
            Map<String,Object> unset = Collections.synchronizedMap(new HashMap<>());
            updaters.forEach(updater->{
                updater.updateDocument(doc,set,unset);
            });
            Map<String,Object> update = Collections.synchronizedMap(new HashMap<>());
            if(set.size()>0)update.put("$set",set);
            if(unset.size()>0)update.put("$unset",unset);
            if(update.size()>0) {
                WriteModel<Document> model = new UpdateOneModel<>(new Document("_id", doc.get("_id")), new Document(update), new UpdateOptions().upsert(false));
                IngestJsonHelper.ingest(collection, Arrays.asList(model), counter);
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,new Document(), IngestPatents.INDEX_NAME, IngestPatents.TYPE_NAME, fields);

        updaters.forEach(StreamableUpdater::finish);
    }
}
