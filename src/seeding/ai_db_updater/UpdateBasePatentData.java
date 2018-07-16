package seeding.ai_db_updater;

import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AppDataDownloader;
import seeding.data_downloader.PatentDataDownloader;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateBasePatentData {
    public static void ingestData(boolean seedApplications) {
        String topLevelTag;
        if(seedApplications) {
            topLevelTag = "us-patent-application";
        } else {
            topLevelTag = "us-patent-grant";
        }
        WebIterator iterator = new ZipFileIterator(seedApplications ? new AppDataDownloader() : new PatentDataDownloader(), seedApplications ? "applications_temp" : "patents_temp", false);
        NestedHandler handler = new USPTOHandler(topLevelTag, seedApplications, false);
        handler.init();
        iterator.applyHandlers(handler);

        System.gc();
        System.gc();
        System.gc();
        Database.close();
    }

    public static void main(String[] args) {
        ingestData(false);
    }
}
