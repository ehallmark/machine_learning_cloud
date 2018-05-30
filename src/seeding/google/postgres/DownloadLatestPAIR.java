package seeding.google.postgres;

import seeding.Database;
import seeding.data_downloader.PAIRDataDownloader;

import java.io.File;
import java.time.LocalDate;

/**
 * Created by Evan on 8/5/2017.
 */
public class DownloadLatestPAIR {
    private static LocalDate lastIngestedDate;
    private static final File lastIngestedDateFile = new File("pair_last_ingest_date.jobj");
    static {
        lastIngestedDate = (LocalDate) Database.tryLoadObject(lastIngestedDateFile);
    }
    public static void main(String[] args) {
        if(lastIngestedDate!=null && lastIngestedDate.isBefore(LocalDate.now().minusDays(7))) {
            PAIRDataDownloader downloader = new PAIRDataDownloader();
            downloader.pullMostRecentData();
            Database.trySaveObject(LocalDate.now(), lastIngestedDateFile);
        } else {
            System.out.println("Skipping redownload of pair...");
        }
    }
}
