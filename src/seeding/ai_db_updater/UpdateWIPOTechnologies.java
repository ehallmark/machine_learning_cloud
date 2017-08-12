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
        File wipoDefinitionDestinationFile = new File("data/wipo_field.tsv");
        File wipoDestinationFile = new File("data/wipo.tsv");
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

        WIPOTechnologyAttribute wipoTechnologyAttribute = new WIPOTechnologyAttribute();
        wipoTechnologyAttribute.initMaps();


        Map<String, String> definitionMap = Collections.synchronizedMap(new HashMap<>());
        {
            {// load from internet
                String baseUrl = "http://www.patentsview.org/data/20170307/wipo_field.zip";
                boolean found = false;
                LocalDate date = LocalDate.now();
                File zipFile = new File("wipo_definition_zip/");
                while (!found) {
                    try {
                        // String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                        String url = baseUrl.replace("?", date.format(DateTimeFormatter.BASIC_ISO_DATE));
                        URL website = new URL(url);
                        System.out.println("Trying: " + website.toString());
                        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                        FileOutputStream fos = new FileOutputStream(zipFile);
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                        fos.close();

                        new ZipFile(zipFile).extractAll(wipoDefinitionDestinationFile.getAbsolutePath());

                        found = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Not found");
                    }
                    date = date.minusDays(1);
                    if (date.isBefore(LocalDate.now().minusYears(3)))
                        throw new RuntimeException("Url does not work: " + baseUrl);
                }
            }

            BufferedReader reader = new BufferedReader(new FileReader(wipoDefinitionDestinationFile));
            reader.lines().skip(1).parallel().forEach(line -> {
                String[] fields = line.split("\t");
                String wipo = fields[0];
                String title = fields[2];
                if (!wipo.startsWith("D")) {
                    System.out.println(wipo + ": " + title);
                    definitionMap.put(wipo, title);
                }
            });
            Database.trySaveObject(definitionMap, WIPOHelper.definitionFile);
        }

        {
            {// load from internet
                String baseUrl = "http://www.patentsview.org/data/?/wipo.zip";
                boolean found = false;
                LocalDate date = LocalDate.now();
                File zipFile = new File("wipo_zip/");
                while (!found) {
                    try {
                        String url = baseUrl.replace("?", date.format(DateTimeFormatter.BASIC_ISO_DATE));
                        URL website = new URL(url);
                        System.out.println("Trying: " + website.toString());
                        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                        FileOutputStream fos = new FileOutputStream(zipFile);
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                        fos.close();

                        new ZipFile(zipFile).extractAll(wipoDefinitionDestinationFile.getAbsolutePath());

                        found = true;
                    } catch (Exception e) {
                        //e.printStackTrace();
                        System.out.println("Not found");
                    }
                    date = date.minusDays(1);
                    if (date.isBefore(LocalDate.now().minusYears(3)))
                        throw new RuntimeException("Url does not work: " + baseUrl);
                }
            }


            BufferedReader reader = new BufferedReader(new FileReader(wipoDestinationFile));
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
                            if(appNum!=null) {
                                wipoTechnologyAttribute.getApplicationDataMap().put(patent,wipo);
                            }
                            wipoTechnologyAttribute.getPatentDataMap().put(patent, wipo);
                            Map<String,Object> data = new HashMap<>();
                            data.put(Constants.WIPO_TECHNOLOGY,wipo);
                            DataIngester.ingestBulk(patent,data,false);
                            if(appNum!=null) {
                                DataIngester.ingestBulk(appNum,data,false);
                            }

                        }
                    } catch (Exception e) {

                    }
                }
            });

            reader.close();
        }

        wipoTechnologyAttribute.save();
        // shutdown bulk
        MyClient.closeBulkProcessor();
    }
}
