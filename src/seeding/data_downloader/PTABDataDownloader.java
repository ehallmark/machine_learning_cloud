package seeding.data_downloader;

import seeding.ai_db_updater.iterators.IngestPTABIterator;
import seeding.google.postgres.SeedingConstants;

import java.io.File;
import java.time.LocalDate;

/**
 * Created by Evan on 8/13/2017.
 */
public class PTABDataDownloader extends FileStreamDataDownloader {
    private static final long serialVersionUID = 1L;
    @Override
    public LocalDate dateFromFileName(String name) {
        return LocalDate.MIN;
    }

    public PTABDataDownloader() {
        super(SeedingConstants.PTAB, IngestPTABIterator.class);
    }

    public File getBackFile() {
        return ((IngestPTABIterator)zipDownloader).getBackfile();
    }

    @Override
    public synchronized void pullMostRecentData() {
        System.out.println("Pulling most recent data...");
        zipDownloader.run(null,failedFiles);
    }
}
