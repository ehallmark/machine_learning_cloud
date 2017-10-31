package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOHandler;
import seeding.ai_db_updater.iterators.DatabaseIterator;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AppDataDownloader;
import seeding.data_downloader.PatentDataDownloader;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePre2005DataFromPatentDB {
    public static void ingestData() {
        SimilarPatentServer.loadAttributes(true);
        Collection<ComputableAttribute> computableAttributes = new HashSet<>(SimilarPatentServer.getAllComputableAttributes());
        computableAttributes.forEach(attr->attr.initMaps());
        DatabaseIterator.setComputableAttributes(computableAttributes);

        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(),1,1).minusYears(25);
        LocalDate endDate = LocalDate.of(2005,1,1);
        DatabaseIterator iterator = new DatabaseIterator(startDate,endDate);
        try {
            iterator.run(false,false); // TODO SET BOTH TO FALSE
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
