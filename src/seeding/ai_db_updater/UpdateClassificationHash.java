package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;
import seeding.data_downloader.AppCPCDataDownloader;
import seeding.data_downloader.PatentCPCDataDownloader;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception {
        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        assetToCPCMap.initMaps();
        assetToCPCMap.getApplicationDataMap().clear(); // repulling everything anyway...
        assetToCPCMap.getPatentDataMap().clear(); // repulling everything anyway...
        {
            AppCPCDataDownloader downloader = new AppCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new AppCPCHandler(assetToCPCMap.getApplicationDataMap()));
            downloader.cleanUp();
        }
        {
            PatentCPCDataDownloader downloader = new PatentCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new PatentCPCHandler(assetToCPCMap.getPatentDataMap()));
            downloader.cleanUp();
        }
        assetToCPCMap.save();
    }

    public static void setupClassificationsHash(File destinationFile, LineHandler handler) {
        Arrays.stream(destinationFile.listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().parallel().forEach(line->{
                    handler.handleLine(line);
                    if (cnt.getAndIncrement() % 100000 == 99999) {
                        System.out.println("Seen "+destinationFile.getName()+" classifications: " + cnt.get());
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        handler.save();

    }
}
