package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.DateIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;

import java.time.LocalDate;

/**
 * Created by Evan on 8/13/2017.
 */
public class AssignmentDataDownloader extends FileStreamDataDownloader {
    public AssignmentDataDownloader() {
        super(Constants.ASSIGNMENTS, IngestUSPTOAssignmentIterator.class, Constants.DEFAULT_START_DATE);
    }
}
