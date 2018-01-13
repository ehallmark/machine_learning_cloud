package seeding.ai_db_updater;

import org.apache.commons.io.FileUtils;
import seeding.ai_db_updater.iterators.FileIterator;
import seeding.ai_db_updater.pair_bulk_data.PAIRHandler;
import seeding.data_downloader.PAIRDataDownloader;

import java.io.File;
import java.time.LocalDate;

/**
 * Created by Evan on 8/5/2017.
 */
public class UpdatePairBulkData {
    public static void main(String[] args) {
        boolean updateElasticSearch = true;
        boolean updatePostgres = false;
        boolean onlyUpdateTermAdjustments = true;

        PAIRDataDownloader downloader = new PAIRDataDownloader();
        downloader.pullMostRecentData();
        FileIterator pairIterator = new FileIterator(downloader.getDestinationFile(),(dir, name) -> {
            try {
                return Integer.valueOf(name.substring(0,4)) >= LocalDate.now().minusYears(30).getYear();
            } catch(Exception e) {
                return false;
            }
        });
        PAIRHandler handler = new PAIRHandler(updatePostgres,updatePostgres,updateElasticSearch,onlyUpdateTermAdjustments);
        handler.init();
        pairIterator.applyHandlers(handler);

        downloader.cleanUp();

    }
}
