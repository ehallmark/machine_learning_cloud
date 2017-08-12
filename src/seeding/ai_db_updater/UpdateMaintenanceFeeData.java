package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import net.lingala.zip4j.core.ZipFile;
import seeding.Database;
import seeding.ai_db_updater.handlers.MaintenanceEventHandler;
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
    public static void main(String[] args) {
        try {
            // update latest assignees
            AssetEntityStatusMap assetEntityStatusMap = new AssetEntityStatusMap();
            AssetToMaintenanceFeeReminderCountMap assetToMaintenanceFeeReminderCountMap = new AssetToMaintenanceFeeReminderCountMap();
            ExpiredAssetsMap expiredAssetsMap = new ExpiredAssetsMap();
            assetEntityStatusMap.initMaps();
            assetToMaintenanceFeeReminderCountMap.initMaps();
            expiredAssetsMap.initMaps();
            System.out.println("Starting to update latest maintenace fee data...");
            loadAndIngestMaintenanceFeeData(new File("maintenance-zip/"),new File("maintenance-dest/"), new MaintenanceEventHandler(expiredAssetsMap,assetEntityStatusMap,assetToMaintenanceFeeReminderCountMap));
            assetEntityStatusMap.save();
            assetToMaintenanceFeeReminderCountMap.save();
            expiredAssetsMap.save();
            MyClient.closeBulkProcessor();
        } catch(Exception sql) {
            sql.printStackTrace();
        }
    }

    public static void loadAndIngestMaintenanceFeeData(File zipFile, File destinationFile, MaintenanceEventHandler handler) throws Exception {
        // should be one at least every other month
        // Load file from Google
        try {
            String url = handler.getUrl(); // "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
            URL website = new URL(url);
            System.out.println("Trying: " + website.toString());
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(zipFile);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();

            new ZipFile(zipFile).extractAll(destinationFile.getAbsolutePath());

        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println("Not found");
        }
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
