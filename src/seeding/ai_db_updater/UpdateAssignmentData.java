package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.USPTOAssignmentHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;


/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    private static void ingestData() {
        FileStreamDataDownloader downloader = new AssignmentDataDownloader();
        LocalDate latestDateToStartFrom = LocalDate.of(2017,1,1);
        Function<File,Boolean> orFunction = file -> {
            String name = file.getName();
            try {
                LocalDate fileDate = LocalDate.parse(name, DateTimeFormatter.BASIC_ISO_DATE);
                if(latestDateToStartFrom.isBefore(fileDate)) return true;
            } catch(Exception e) {
            }
            return false;
        };
        WebIterator iterator = new ZipFileIterator(downloader, "assignments_temp", true, false, orFunction);
        NestedHandler handler = new USPTOAssignmentHandler();
        handler.init();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        ingestData();
    }
}
