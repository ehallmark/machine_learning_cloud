package seeding.data_downloader;

import lombok.Getter;
import models.classification_models.WIPOHelper;
import net.lingala.zip4j.core.ZipFile;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

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

/**
 * Created by Evan on 8/13/2017.
 */
public class WIPOTechnologyDownloader extends SingleFileDownloader {
    @Getter
    private Map<String,String> definitionMap;

    public WIPOTechnologyDownloader() {
        super(new File("data/wipo/"), new File("data/wipo.zip"), Constants.WIPO_TECHNOLOGY_URL_CREATOR);
    }

    @Override
    public void pullMostRecentData() {
        definitionMap = Collections.synchronizedMap(new HashMap<>());
        File wipoDefinitionDestinationFile = new File("data/wipo_field/");
        {
            {// load from internet
                if(wipoDefinitionDestinationFile.exists()) wipoDefinitionDestinationFile.delete();
                String baseUrl = "http://www.patentsview.org/data/?/wipo_field.zip";
                boolean found = false;
                LocalDate date = LocalDate.now();
                File wipoDefinitionZipFile = new File("wipo_definition_zip/");
                if(wipoDefinitionZipFile.exists()) wipoDefinitionZipFile.delete();
                while (!found) {
                    try {
                        // String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                        String url = baseUrl.replace("?", date.format(DateTimeFormatter.BASIC_ISO_DATE));
                        URL website = new URL(url);
                        System.out.println("Trying: " + website.toString());
                        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                        FileOutputStream fos = new FileOutputStream(wipoDefinitionZipFile);
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                        fos.close();

                        new ZipFile(wipoDefinitionZipFile).extractAll(wipoDefinitionDestinationFile.getAbsolutePath());

                        found = true;
                    } catch (Exception e) {
                        System.out.println("Not found");
                    }
                    date = date.minusDays(1);
                    if (date.isBefore(LocalDate.now().minusYears(3)))
                        throw new RuntimeException("Url does not work: " + baseUrl);
                }
            }


            Arrays.stream(wipoDefinitionDestinationFile.listFiles()).forEach(file->{
                try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    reader.lines().skip(1).parallel().forEach(line -> {
                        String[] fields = line.split("\t");
                        String wipo = fields[0];
                        String title = fields[2];
                        if (!wipo.startsWith("D")) {
                            System.out.println(wipo + ": " + title);
                            definitionMap.put(wipo, title);
                        }
                    });
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });

            Database.trySaveObject(definitionMap, WIPOHelper.definitionFile);
        }

        super.pullMostRecentData();

    }
}
