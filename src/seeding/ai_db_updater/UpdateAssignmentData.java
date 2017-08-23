package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOAssignmentHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;

import java.util.Arrays;


/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    private static void ingestData() {
        FileStreamDataDownloader downloader = new AssignmentDataDownloader();
        WebIterator iterator = new ZipFileIterator(downloader, "assignments_temp", false, false);
        NestedHandler handler = new USPTOAssignmentHandler();
        handler.init();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        ingestData();
    }
}
