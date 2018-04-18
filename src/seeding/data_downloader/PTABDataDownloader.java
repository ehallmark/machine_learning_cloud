package seeding.data_downloader;

import com.sun.org.apache.bcel.internal.classfile.ConstantString;
import seeding.Constants;
import seeding.ai_db_updater.iterators.IngestPTABIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;
import seeding.google.postgres.SeedingConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Created by Evan on 8/13/2017.
 */
public class PTABDataDownloader extends FileStreamDataDownloader {
    @Override
    public LocalDate dateFromFileName(String name) {
        return LocalDate.MIN;
    }

    public PTABDataDownloader() {
        super(SeedingConstants.PTAB, IngestPTABIterator.class, null);
    }


    @Override
    public synchronized void pullMostRecentData() {
        System.out.println("Pulling most recent data...");
        zipDownloader.run(null,failedDates);
    }
}
