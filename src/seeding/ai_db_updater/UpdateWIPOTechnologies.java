package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import seeding.Constants;
import seeding.data_downloader.WIPOTechnologyDownloader;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateWIPOTechnologies {
    static final AtomicLong cnt = new AtomicLong(0);
    public static void main(String[] args) throws Exception {
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

        // Data loader
        WIPOTechnologyDownloader downloader = new WIPOTechnologyDownloader();
        downloader.pullMostRecentData();
        Map<String,String> definitionMap = downloader.getDefinitionMap();

        if(definitionMap==null) {
            throw new RuntimeException("Unable to create definition map... map is null");
        }

        // handle data
        Arrays.stream(downloader.getDestinationFile().listFiles()).forEach(file->{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().skip(1).parallel().forEach(line -> {
                    String[] fields = line.split("\t");
                    String patent = fields[0];
                    String wipo = fields[1];
                    try {
                        String filing = assetToFilingMap.getPatentDataMap().get(patent);
                        if (filing != null) {
                            String wipoTechnology;
                            if (patent.startsWith("D")) {
                                wipoTechnology = "Design";
                            } else if (patent.startsWith("P")) {
                                wipoTechnology = "Plants";
                            } else {
                                wipoTechnology = definitionMap.get(wipo);
                            }

                            if (wipoTechnology != null) {
                                Map<String, Object> data = new HashMap<>();
                                data.put(Constants.WIPO_TECHNOLOGY, wipoTechnology);
                                DataIngester.ingestBulk(null, filing, data, false);
                                if (cnt.getAndIncrement() % 100000 == 99999) {
                                    System.out.println("Seen " + cnt.get() + " wipo technologies...");
                                }
                            }
                        }
                    } catch (Exception e) {

                    }
                });
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        downloader.cleanUp();
    }
}
