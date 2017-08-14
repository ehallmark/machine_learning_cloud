package seeding.ai_db_updater;

import elasticsearch.DataIngester;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception {
        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        assetToCPCMap.initMaps();
        {
            PatentCPCDataDownloader downloader = new PatentCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new PatentCPCHandler(assetToCPCMap.getPatentDataMap()));
        }
        {
            AppCPCDataDownloader downloader = new AppCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new AppCPCHandler(assetToCPCMap.getApplicationDataMap()));
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
