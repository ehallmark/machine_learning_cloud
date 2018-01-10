package seeding;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import user_interface.ui_models.attributes.computable_attributes.PriorityDateComputedAttribute;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.RelatedAssetsAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by Evan on 1/10/2018.
 */
public class CreatePriorityDateMapFromMongoDB {

    public static void main(String[] args) throws Exception {
        Map<String,LocalDate> map = Collections.synchronizedMap(new HashMap<>());

        final String type = DataIngester.TYPE_NAME;
        final Document query = new Document();
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger valid = new AtomicInteger(0);

        final RelatedAssetsAttribute relatedAssetsAttribute = new RelatedAssetsAttribute();
        final AtomicInteger epoch = new AtomicInteger(0);
        final Consumer<Document> consumer = doc -> {
            Object name = doc.get("_id");
            Object filing = doc.get(Constants.FILING_NAME);
            //System.out.print("-");
            Object priorityDate = doc.getOrDefault(Constants.PRIORITY_DATE,doc.get(Constants.FILING_DATE));
            if(priorityDate!=null&&filing!=null) {
                LocalDate minPriority = LocalDate.parse(priorityDate.toString(), DateTimeFormatter.ISO_DATE);
                List<String> related = relatedAssetsAttribute.getApplicationDataMap().getOrDefault(name,relatedAssetsAttribute.getPatentDataMap().get(name));
                if(related!=null) {
                    for(String relation : related) {
                        LocalDate relatedPriorityDate = map.get(relation);
                        if(relatedPriorityDate!=null&&relatedPriorityDate.isBefore(minPriority)) {
                            minPriority=relatedPriorityDate;
                        }
                    }
                }
                valid.getAndIncrement();
                map.put(filing.toString(),minPriority);
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Epoch "+epoch.get()+" Valid: "+valid.get());
            }
        };

        System.out.println("Starting first iteration...");
        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,query,type,Constants.FILING_DATE,Constants.PRIORITY_DATE,Constants.FILING_NAME);
        epoch.set(1);
        System.out.println("Iterating again to complete minimum priority dates...");
        // run it again to guarantee min priority date for all documents (not just some relatives)
        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,query,type,Constants.FILING_DATE,Constants.PRIORITY_DATE,Constants.FILING_NAME);

        System.out.println("Saving...");
        new PriorityDateComputedAttribute().saveMap(map);
        System.out.println("Saved.");
    }
}
