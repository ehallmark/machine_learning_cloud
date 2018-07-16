package seeding;

import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AppDataDownloader;
import seeding.data_downloader.PatentDataDownloader;

import java.io.File;
import java.util.function.Function;

/**
 * Created by Evan on 12/9/2017.
 */
public class TestUpdateAll {
    public static void main(String[] args) throws Exception {
        boolean seedApplications = false;
        Function<File,Boolean> orFunction = something->true;
        // test update patent grant
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
