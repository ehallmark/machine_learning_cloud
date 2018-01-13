package seeding;

import com.mongodb.client.model.Filters;
import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import elasticsearch.MongoDBClient;
import org.bson.Document;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCitedAssetsMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/10/2018.
 */
public class CleanUpCitationDocumentNamesFromMongo {

    public static void main(String[] args)  {
        final boolean testing = false;

        final String[] fields = new String[]{Constants.CITATIONS};
        AssetToCitedAssetsMap assetToCitedAssetsMap = new AssetToCitedAssetsMap();
        assetToCitedAssetsMap.getApplicationDataMap();
        assetToCitedAssetsMap.getPatentDataMap();

        final String type = DataIngester.TYPE_NAME;
        final Document query = new Document();
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger valid = new AtomicInteger(0);
        final Consumer<Document> consumer = doc -> {
            Object id = doc.get("_id");
            //System.out.print("-");
            if(id!=null) {
                List<Map<String,Object>> data = (List<Map<String,Object>>)doc.get(fields[0]);
                if (data != null) {
                    valid.getAndIncrement();
                    AtomicBoolean change = new AtomicBoolean(false);
                    data = data.stream().map(d->{
                        Object docKind = d.get(Constants.DOC_KIND);
                        if(docKind!=null&&docKind.equals("A")) {
                            Object docName = d.get(Constants.NAME);
                            if(docName!=null&&!docName.toString().contains("/")) {
                                Object newDoc = Flag.filingDocumentHandler.apply(null).apply(docName.toString());
                                if(newDoc!=null) {
                                    d.put(Constants.NAME, newDoc.toString());
                                    change.set(true);
                                }
                            }
                        }
                        return d;
                    }).collect(Collectors.toList());
                    List<String> citedAssets = data.stream().map(d->d.get(Constants.NAME)).filter(d->d!=null).map(d->d.toString()).collect(Collectors.toList());
                    if(change.get()&&citedAssets.size()>0) {
                        if (assetToCitedAssetsMap.getPatentDataMap().containsKey(id.toString())) {
                            assetToCitedAssetsMap.getPatentDataMap().put(id.toString(), citedAssets);
                        } else if (assetToCitedAssetsMap.getApplicationDataMap().containsKey(id.toString())) {
                            assetToCitedAssetsMap.getApplicationDataMap().put(id.toString(), citedAssets);
                        }
                    }
                    if(change.get()) {
                        if(testing)System.out.println("Updated "+fields[0]+" for "+id);
                        MongoDBClient.get().getDatabase(DataIngester.INDEX_NAME).getCollection(DataIngester.TYPE_NAME).updateOne(Filters.eq("_id", id), new Document("$set", new Document(fields[0], data)), (v, t) -> {
                            if (t != null) {
                                System.out.println("Failed in update: " + t.getMessage());
                            }
                        });
                    }
                }
            }
            if(cnt.getAndIncrement()%100000==99999) {

                System.out.println("Valid: "+valid.get());
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,query,type,fields);

        assetToCitedAssetsMap.save();
    }
}
