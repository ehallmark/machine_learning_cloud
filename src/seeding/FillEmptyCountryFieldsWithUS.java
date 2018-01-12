package seeding;

import com.mongodb.client.model.Filters;
import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import elasticsearch.MongoDBClient;
import org.bson.Document;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/10/2018.
 */
public class FillEmptyCountryFieldsWithUS {

    public static void main(String[] args)  {
        final boolean testing = true;

        final String[] fields = new String[]{Constants.ASSIGNEES,Constants.INVENTORS,Constants.APPLICANTS,Constants.AGENTS,Constants.PATENT_FAMILY,Constants.CITATIONS};

        final String type = DataIngester.TYPE_NAME;
        final Document query = new Document();
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger valid = new AtomicInteger(0);
        Map<String,AtomicInteger> fieldToNumChanged = Collections.synchronizedMap(new HashMap<>());
        for(String field : fields) {
            fieldToNumChanged.put(field,new AtomicInteger(0));
        }
        final Consumer<Document> consumer = doc -> {
            Object id = doc.get("_id");
            //System.out.print("-");
            if(id!=null) {
                for (String field : fields) {
                    List<Map<String,Object>> data = (List<Map<String,Object>>)doc.get(field);
                    if(data==null) {
                        System.out.println("NULL FOR "+field);
                    }
                    if (data != null) {
                        valid.getAndIncrement();
                        AtomicBoolean change = new AtomicBoolean(false);
                        data = data.stream().map(d->{
                            if((field.equals(Constants.CITATIONS)||d.containsKey(Constants.STATE))&&!d.containsKey(Constants.COUNTRY)) {
                                d.put(Constants.COUNTRY,"US");
                                change.set(true);
                            }
                            return d;
                        }).collect(Collectors.toList());
                        if(change.get()) {
                            fieldToNumChanged.get(field).getAndIncrement();
                            if(testing)System.out.println("Updated "+field+" for "+id);
                            if(!testing) {
                                MongoDBClient.get().getDatabase(DataIngester.INDEX_NAME).getCollection(DataIngester.TYPE_NAME).updateOne(Filters.eq("_id", id), new Document("$set", new Document(field, data)), (v, t) -> {
                                    if (t != null) {
                                        System.out.println("Failed in update: " + t.getMessage());
                                    }
                                });
                            }
                        }
                    }
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Valid: "+valid.get());
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,query,type,fields);

        fieldToNumChanged.entrySet().forEach(e->{
            System.out.println("Updated "+e.getKey()+": "+e.getValue().get());
        });
    }
}
