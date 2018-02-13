package seeding;

import elasticsearch.DataIngester;
import seeding.ai_db_updater.iterators.DatabaseIterator;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;

public class RerunCitationIngestPre2005 {
    public static void ingestData() {
       // SimilarPatentServer.loadAttributes(true);
        Collection<ComputableAttribute> computableAttributes = new HashSet<>(); //new HashSet<>(SimilarPatentServer.getAllComputableAttributes());
        //computableAttributes.forEach(attr->attr.getPatentDataMap());
        DatabaseIterator.setComputableAttributes(computableAttributes);

        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(),1,1).minusYears(25);
        LocalDate endDate = LocalDate.of(2005,1,1);
        DatabaseIterator iterator = new DatabaseIterator(startDate,endDate);
        try {
            iterator.run(false,false,true);
            iterator.save();
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        DataIngester.finishCurrentMongoBatch();
    }

    public static void main(String[] args) {
        ingestData();
    }
}
