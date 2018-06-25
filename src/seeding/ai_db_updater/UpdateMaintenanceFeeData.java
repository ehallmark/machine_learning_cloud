package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.MaintenanceEventHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateMaintenanceFeeData {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception{
        // update latest assignees
        System.out.println("Starting to update latest maintenance fee data...");
       // MaintenanceFeeDataDownloader downloader = new MaintenanceFeeDataDownloader();
       // downloader.pullMostRecentData();
        System.out.println("Starting to ingest data...");
        ////ingestMaintenanceFeeData(downloader.getDestinationFile(), new MaintenanceEventHandler(null));
        //downloader.cleanUp();
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
            }
        });
        handler.save();
    }
}
