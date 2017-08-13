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

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateMaintenanceFeeData {
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
        MyClient.closeBulkProcessor();
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
                String line = reader.readLine();
                while (line != null) {
                    handler.handleLine(line);
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                file.delete();
            }
        });
        handler.save();
    }
}
