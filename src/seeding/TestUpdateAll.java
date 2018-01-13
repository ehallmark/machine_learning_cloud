package seeding;

import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AppDataDownloader;
import seeding.data_downloader.PatentDataDownloader;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;

/**
 * Created by Evan on 12/9/2017.
 */
public class TestUpdateAll {
    public static void main(String[] args) throws Exception {
        boolean seedApplications = false;
        Function<File,Boolean> orFunction = something->true; /*file -> {
            String name = file.getName();
            try {
                LocalDate fileDate = LocalDate.parse(name, DateTimeFormatter.ISO_DATE);
                return fileDate.getYear()>2015;
            } catch(Exception e) {
            }
            return false;
        };*/

        // test update patent grant
        SimilarPatentServer.loadAttributes(true);
        Collection<ComputableAttribute> computableAttributes = new HashSet<>(SimilarPatentServer.getAllComputableAttributes());
        computableAttributes.forEach(attr->attr.initMaps());
        USPTOHandler.setComputableAttributes(computableAttributes);
        String topLevelTag;
        if(seedApplications) {
            topLevelTag = "us-patent-application";
        } else {
            topLevelTag = "us-patent-grant";
        }
        WebIterator iterator = new ZipFileIterator(seedApplications ? new AppDataDownloader() : new PatentDataDownloader(), seedApplications ? "applications_temp" : "patents_temp",false,true,orFunction,true);
        NestedHandler handler = new USPTOHandler(topLevelTag, seedApplications,true);
        handler.init();
        iterator.applyHandlers(handler);
    }
}
