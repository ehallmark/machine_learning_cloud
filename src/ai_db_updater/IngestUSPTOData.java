package ai_db_updater;

import ai_db_updater.handlers.CitationSAXHandler;
import ai_db_updater.handlers.InventionTitleSAXHandler;
import ai_db_updater.iterators.IngestUSPTOAssignmentIterator;
import ai_db_updater.iterators.IngestUSPTOIterator;
import ai_db_updater.iterators.PatentGrantIterator;
import ai_db_updater.tools.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestUSPTOData {

    public static void main(String[] args) {
        IngestUSPTOIterator patentIterator = new IngestUSPTOIterator(Constants.DEFAULT_START_DATE, "data/patents/", Constants.GOOGLE_URL_CREATOR, Constants.USPTO_URL_CREATOR);
        //patentIterator.run();

        IngestUSPTOIterator appIterator = new IngestUSPTOIterator(Constants.DEFAULT_START_DATE, "data/applications/", Constants.GOOGLE_APP_URL_CREATOR, Constants.USPTO_APP_URL_CREATOR);
        //appIterator.run();

        IngestUSPTOAssignmentIterator assignmentIterator = new IngestUSPTOAssignmentIterator("data/assignments/");
        assignmentIterator.run();
    }
}
