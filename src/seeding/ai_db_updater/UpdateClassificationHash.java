package seeding.ai_db_updater;

import elasticsearch.MyClient;
import net.lingala.zip4j.core.ZipFile;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;
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
                setupClassificationsHash(new File("patent-cpc-zip/"), new File("patent-cpc-dest/"), Constants.PATENT_CPC_URL_CREATOR, new PatentCPCHandler(assetToCPCMap.getPatentDataMap()));
            }
        });
        pool.execute(new RecursiveAction() {
            @Override
            protected void compute() {
                setupClassificationsHash(new File("app-cpc-zip/"), new File("app-cpc-dest/"), Constants.APP_CPC_URL_CREATOR, new AppCPCHandler(assetToCPCMap.getApplicationDataMap()));
            }
        });

        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        assetToCPCMap.save();
        // shutdown bulk
        MyClient.closeBulkProcessor();
    }

    public static void setupClassificationsHash(File zipFile, File destinationFile, UrlCreator urlCreator, LineHandler handler) {
        // should be one at least every other month
        // Load file from Google
        boolean found = false;
        LocalDate date = LocalDate.now();
        while (!found) {
            try {
                // String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                String url = urlCreator.create(date); //"http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
                URL website = new URL(url);
                System.out.println("Trying: " + website.toString());
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(zipFile);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();

                new ZipFile(zipFile).extractAll(destinationFile.getAbsolutePath());

                found = true;
            } catch (Exception e) {
                //e.printStackTrace();
                System.out.println("Not found");
            }
            date = date.minusDays(1);
            if(date.isBefore(LocalDate.now().minusYears(20))) throw new RuntimeException("Url does not work: "+urlCreator.create(date));
        }

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
