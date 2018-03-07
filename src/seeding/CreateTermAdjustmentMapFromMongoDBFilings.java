package seeding;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import user_interface.ui_models.attributes.computable_attributes.TermAdjustmentAttribute;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by Evan on 1/10/2018.
 */
public class CreateTermAdjustmentMapFromMongoDBFilings {

    public static void main(String[] args) throws Exception {
        Map<String,Integer> map = Collections.synchronizedMap(new HashMap<>());

        final String type = "filings";
        final Document query = new Document();
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger valid = new AtomicInteger(0);
        final Consumer<Document> consumer = doc -> {
            Object filing = doc.get("_id");
            //System.out.print("-");
            if(filing!=null) {
                Object termAdjustment = doc.get(Constants.PATENT_TERM_ADJUSTMENT);
                if(termAdjustment!=null) {
                    valid.getAndIncrement();
                    map.put(filing.toString(),Integer.valueOf(termAdjustment.toString()));
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Valid: "+valid.get());
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,query,type,Constants.PATENT_TERM_ADJUSTMENT);

        System.out.println("Saving...");
        new TermAdjustmentAttribute().saveMap(map);
        System.out.println("Saved.");
    }
}
