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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateAppClassificationHash {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception {
        Connection conn = Database.getConn();
        if (!conn.getAutoCommit()) {
            conn.setAutoCommit(true);
        }
        {
            AppCPCDataDownloader downloader = new AppCPCDataDownloader();
            downloader.pullMostRecentData();
            setupClassificationsHash(downloader.getDestinationFile(), new AppCPCHandler(conn));
            downloader.cleanUp();
        }
    }
    public static void ingestResult(String key, String code, Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement("insert into big_query_cpc (publication_number_full, code) values (?, ?) on conflict do nothing");
            ps.setString(1, key);
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setupClassificationsHash(File destinationFile, LineHandler handler) {
        Arrays.stream(destinationFile.listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line->{
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
