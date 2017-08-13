package seeding.ai_db_updater;

import elasticsearch.MyClient;
import net.lingala.zip4j.core.ZipFile;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;
import seeding.data_downloader.AppCPCDataDownloader;
import seeding.data_downloader.PatentCPCDataDownloader;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    public static void main(String[] args) throws Exception {
        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        assetToCPCMap.initMaps();
        ForkJoinPool pool = new ForkJoinPool(2);
        pool.execute(new RecursiveAction() {
            @Override
            protected void compute() {
                PatentCPCDataDownloader downloader = new PatentCPCDataDownloader();
                downloader.pullMostRecentData();
                setupClassificationsHash(downloader.getDestinationFile(), new PatentCPCHandler(assetToCPCMap.getPatentDataMap()));
            }
        });
        pool.execute(new RecursiveAction() {
            @Override
            protected void compute() {
                AppCPCDataDownloader downloader = new AppCPCDataDownloader();
                downloader.pullMostRecentData();
                setupClassificationsHash(downloader.getDestinationFile(), new AppCPCHandler(assetToCPCMap.getApplicationDataMap()));
            }
        });

        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        assetToCPCMap.save();
        // shutdown bulk
        MyClient.closeBulkProcessor();
    }

    public static void setupClassificationsHash(File destinationFile, LineHandler handler) {
        Arrays.stream(destinationFile.listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    handler.handleLine(line);
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        handler.save();

    }
}
