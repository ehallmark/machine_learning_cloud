package seeding.google.postgres;

import seeding.ai_db_updater.iterators.FileIterator;
import seeding.ai_db_updater.pair_bulk_data.PAIRHandler;
import seeding.data_downloader.PAIRDataDownloader;

import java.time.LocalDate;

/**
 * Created by Evan on 8/5/2017.
 */
public class DownloadLatestPAIR {
    public static void main(String[] args) {
        PAIRDataDownloader downloader = new PAIRDataDownloader();
        downloader.pullMostRecentData();
    }
}
