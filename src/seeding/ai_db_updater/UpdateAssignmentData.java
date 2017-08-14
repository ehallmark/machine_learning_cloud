package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import seeding.Constants;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOAssignmentHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;


/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    private static void ingestData() {
        FileStreamDataDownloader downloader = new AssignmentDataDownloader();
        WebIterator iterator = new ZipFileIterator(downloader, "assignments_temp");
        NestedHandler handler = new USPTOAssignmentHandler();
        handler.init();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        ingestData();
    }
}
