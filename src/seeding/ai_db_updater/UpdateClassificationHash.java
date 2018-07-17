package seeding.ai_db_updater;

import seeding.Database;
import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;
import seeding.data_downloader.AppCPCDataDownloader;
import seeding.data_downloader.PatentCPCDataDownloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception {
        Map<String,Set<String>> patentCPCMap = new HashMap<>();
        Map<String,Set<String>> appCPCMap = new HashMap<>();
        {
            AppCPCDataDownloader downloader = new AppCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new AppCPCHandler(appCPCMap));
            downloader.cleanUp();
        }
        {
            PatentCPCDataDownloader downloader = new PatentCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new PatentCPCHandler(patentCPCMap));
            downloader.cleanUp();
        }
        Connection conn = Database.getConn();
        ingestResults(patentCPCMap, conn);
        ingestResults(appCPCMap, conn);
        conn.close();
    }

    private static void ingestResults(Map<String,Set<String>> map, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("update patents_global set code = ? where publication_number_full=? and code is null");
        long count = 0L;
        for(Map.Entry<String,Set<String>> entry : map.entrySet()) {
            ps.setArray(1, conn.createArrayOf("varchar", entry.getValue().toArray(new String[entry.getValue().size()])));
            ps.setString(2, entry.getKey());
            ps.executeUpdate();
            if(count % 100000 == 99999) {
                System.out.println("Finished "+count);
                conn.commit();
            }
            count++;
        }
        conn.commit();
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
