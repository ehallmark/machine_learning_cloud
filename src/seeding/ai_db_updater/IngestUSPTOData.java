package seeding.ai_db_updater;

import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOIterator;
import seeding.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestUSPTOData {

    public static void main(String[] args) {
        IngestUSPTOIterator patentIterator = new IngestUSPTOIterator(Constants.DEFAULT_START_DATE, Constants.PATENT_ZIP_FOLDER, Constants.GOOGLE_URL_CREATOR, Constants.USPTO_URL_CREATOR);
        patentIterator.run();

        IngestUSPTOIterator appIterator = new IngestUSPTOIterator(Constants.DEFAULT_START_DATE, Constants.APP_ZIP_FOLDER, Constants.GOOGLE_APP_URL_CREATOR, Constants.USPTO_APP_URL_CREATOR);
        //appIterator.run();

        IngestUSPTOAssignmentIterator assignmentIterator = new IngestUSPTOAssignmentIterator(Constants.ASSIGNMENT_ZIP_FOLDER);
        assignmentIterator.run();
    }
}
