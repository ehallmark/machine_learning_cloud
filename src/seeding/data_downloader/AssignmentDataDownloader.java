package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.DateIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Created by Evan on 8/13/2017.
 */
public class AssignmentDataDownloader extends FileStreamDataDownloader {
    @Override
    public LocalDate dateFromFileName(String name) {
        if(name.length()==6) name = "20"+name;
        if(name.length()==5) name = "200"+name;
        return LocalDate.parse(name, DateTimeFormatter.BASIC_ISO_DATE);
    }

    public AssignmentDataDownloader() {
        super(Constants.ASSIGNMENTS, IngestUSPTOAssignmentIterator.class, Constants.DEFAULT_ASSIGNMENT_START_DATE);
    }
}
