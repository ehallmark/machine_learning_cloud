package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import net.lingala.zip4j.core.ZipFile;
import seeding.Database;
import seeding.ai_db_updater.handlers.MaintenanceEventHandler;
import seeding.data_downloader.MaintenanceFeeDataDownloader;
import user_interface.ui_models.attributes.hidden_attributes.AssetEntityStatusMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToMaintenanceFeeReminderCountMap;
import user_interface.ui_models.attributes.hidden_attributes.ExpiredAssetsMap;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateMaintenanceFeeData {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception{
        // update latest assignees
        AssetEntityStatusMap assetEntityStatusMap = new AssetEntityStatusMap();
        AssetToMaintenanceFeeReminderCountMap assetToMaintenanceFeeReminderCountMap = new AssetToMaintenanceFeeReminderCountMap();
        ExpiredAssetsMap expiredAssetsMap = new ExpiredAssetsMap();
        assetEntityStatusMap.initMaps();
        assetToMaintenanceFeeReminderCountMap.initMaps();
        expiredAssetsMap.initMaps();
        System.out.println("Starting to update latest maintenance fee data...");
        MaintenanceFeeDataDownloader downloader = new MaintenanceFeeDataDownloader();
        downloader.pullMostRecentData();
        System.out.println("Starting to ingest data...");
        ingestMaintenanceFeeData(downloader.getDestinationFile(), new MaintenanceEventHandler(expiredAssetsMap,assetEntityStatusMap,assetToMaintenanceFeeReminderCountMap));
        assetEntityStatusMap.save();
        assetToMaintenanceFeeReminderCountMap.save();
        expiredAssetsMap.save();
    }

    public static void ingestMaintenanceFeeData(File destinationFile, MaintenanceEventHandler handler) throws Exception {
        // should be one at least every other month
        // Load file from Google
        Arrays.stream(destinationFile.listFiles()).forEach(file -> {
            if (!file.getName().endsWith(".txt")) {
                file.delete();
                return;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line->{
                    handler.handleLine(line);
                    if (cnt.getAndIncrement() % 100000 == 99999) {
                        System.out.println("Seen maintenance events: " + cnt.get());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                file.delete();
            }
        });
        handler.save();
    }
}
