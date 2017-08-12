package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOAssignmentHandler;
import seeding.ai_db_updater.iterators.AssignmentIterator;
import seeding.ai_db_updater.handlers.AssignmentSAXHandler;
import seeding.Constants;
import seeding.ai_db_updater.handlers.TransactionSAXHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import user_interface.server.SimilarPatentServer;

import java.io.File;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    private static void ingestData() {
        WebIterator iterator = new ZipFileIterator(new File("data/assignments"), "temp_dir_test",(a, b)->true);
        NestedHandler handler = new USPTOAssignmentHandler();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        ingestData();
    }
}
