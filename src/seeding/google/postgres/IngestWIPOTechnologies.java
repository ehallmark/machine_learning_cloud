package seeding.google.postgres;

import models.classification_models.WIPOHelper;
import seeding.Database;
import seeding.data_downloader.WIPOTechnologyDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 1/20/17.
 */
public class IngestWIPOTechnologies {
    static final AtomicLong cnt = new AtomicLong(0);

    public static void main(String[] args) throws SQLException {
        Connection conn = Database.getConn();

        // Data loader
        WIPOTechnologyDownloader downloader = new WIPOTechnologyDownloader();
        downloader.pullMostRecentData();
        Map<String,String> definitionMap = downloader.getDefinitionMap();

        if(definitionMap==null) {
            throw new RuntimeException("Unable to create definition map... map is null");
        }

        final String valueStr = "(?,?,?)";
        final String sql = "insert into big_query_wipo (publication_number,sequence,wipo_technology) values "+valueStr+" on conflict (publication_number,sequence) do update set wipo_technology=excluded.wipo_technology";

        final String[] wipoFields = new String[]{
                SeedingConstants.PUBLICATION_NUMBER,
                SeedingConstants.SEQUENCE,
                SeedingConstants.WIPO_TECHNOLOGY
        };

        DefaultApplier applier = new DefaultApplier(false, conn, wipoFields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        File rootFile = downloader.getDestinationFile();
        while (rootFile.isDirectory()) {
            rootFile = rootFile.listFiles()[0];
        }
        rootFile = rootFile.getParentFile();

        // handle data
        Arrays.stream(rootFile.listFiles()).forEach(file->{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().skip(1).parallel().forEach(line -> {
                    String[] fields = line.split("\t");
                    if(fields.length<3) return;
                    String patent = fields[0];
                    String wipo = fields[1];
                    int sequence = Integer.valueOf(fields[2]);
                    try {
                        String wipoTechnology;
                        if (patent.startsWith("D")) {
                            wipoTechnology = WIPOHelper.DESIGN_TECHNOLOGY;
                        } else if (patent.startsWith("P")) {
                            wipoTechnology = WIPOHelper.PLANT_TECHNOLOGY;
                        } else {
                            wipoTechnology = definitionMap.get(wipo);
                        }

                        if (wipoTechnology != null) {
                            queryStream.ingest(Arrays.asList(patent,sequence,wipoTechnology));
                            if (cnt.getAndIncrement() % 100000 == 99999) {
                                System.out.println("Seen " + cnt.get() + " wipo technologies...");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        queryStream.close();
        downloader.cleanUp();
    }
}
