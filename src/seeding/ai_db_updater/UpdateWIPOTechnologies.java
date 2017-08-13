package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import models.classification_models.WIPOHelper;
import net.lingala.zip4j.core.ZipFile;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;
import seeding.data_downloader.WIPOTechnologyDownloader;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateWIPOTechnologies {
    public static void main(String[] args) throws Exception {
        File wipoDestinationFile = new File("data/wipo/");
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

        WIPOTechnologyAttribute wipoTechnologyAttribute = new WIPOTechnologyAttribute();
        wipoTechnologyAttribute.initMaps();

        // Data loader
        WIPOTechnologyDownloader downloader = new WIPOTechnologyDownloader();
        downloader.pullMostRecentData();
        Map<String,String> definitionMap = downloader.getDefinitionMap();

        if(definitionMap==null) {
            throw new RuntimeException("Unable to create definition map... map is null");
        }

        // handle data
        Arrays.stream(wipoDestinationFile.listFiles()).forEach(file->{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().skip(1).parallel().forEach(line -> {
                    String[] fields = line.split("\t");
                    String patent = fields[0];
                    String wipo = fields[1];
                    if (definitionMap.containsKey(wipo)) {
                        try {
                            if (Integer.valueOf(patent) > 6000000) {
                                String filing = assetToFilingMap.getPatentDataMap().get(patent);
                                String appNum = null;
                                if (filing != null) {
                                    appNum = filingToAssetMap.getApplicationDataMap().get(filing);
                                }
                                if (appNum != null) {
                                    wipoTechnologyAttribute.getApplicationDataMap().put(patent, wipo);
                                }
                                wipoTechnologyAttribute.getPatentDataMap().put(patent, wipo);
                                Map<String, Object> data = new HashMap<>();
                                data.put(Constants.WIPO_TECHNOLOGY, wipo);
                                DataIngester.ingestBulk(patent, data, false);
                                if (appNum != null) {
                                    DataIngester.ingestBulk(appNum, data, false);
                                }

                            }
                        } catch (Exception e) {

                        }
                    }
                });
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        wipoTechnologyAttribute.save();
        // shutdown bulk
        MyClient.closeBulkProcessor();
    }
}
