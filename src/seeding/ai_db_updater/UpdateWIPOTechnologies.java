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
                    try {
                        if (patent.startsWith("RE") || patent.startsWith("D") || patent.startsWith("PP") || Integer.valueOf(patent) > 6000000) {
                            String wipoTechnology;
                            if(patent.startsWith("D")) {
                                wipoTechnology = "Design";
                            } else if (patent.startsWith("PP")) {
                                wipoTechnology = "Plants";
                            } else {
                                wipoTechnology = definitionMap.get(wipo);
                            }

                            if(wipoTechnology!=null) {
                                String filing = assetToFilingMap.getPatentDataMap().get(patent);
                                Collection<String> appNums = null;
                                if (filing != null) {
                                    appNums = filingToAssetMap.getApplicationDataMap().get(filing);
                                }
                                if (appNums != null) {
                                    appNums.forEach(appNum->{
                                        wipoTechnologyAttribute.getApplicationDataMap().put(appNum, wipoTechnology);
                                    });
                                }
                                wipoTechnologyAttribute.getPatentDataMap().put(patent, wipoTechnology);
                                Map<String, Object> data = new HashMap<>();
                                data.put(Constants.WIPO_TECHNOLOGY, wipoTechnology);
                                DataIngester.ingestBulk(patent, data, false);
                                if (appNums != null) {
                                    appNums.forEach(appNum->{
                                        DataIngester.ingestBulk(appNum, data, false);
                                    });
                                }
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

        wipoTechnologyAttribute.save();
    }
}
