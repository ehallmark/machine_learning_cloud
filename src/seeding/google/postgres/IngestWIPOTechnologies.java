package seeding.google.postgres;

import models.classification_models.WIPOHelper;
import seeding.Database;
import seeding.data_downloader.WIPOTechnologyDownloader;
import seeding.google.attributes.Constants;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
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

        final String valueStr = "(?,?)";
        final String sql = "insert into big_query_wipo (publication_number,wipo_technology) values "+valueStr+" on conflict do nothing";

        final String[] wipoFields = new String[]{
                Constants.PUBLICATION_NUMBER,
                Constants.WIPO_TECHNOLOGY
        };

        DefaultApplier applier = new DefaultApplier(false, conn, wipoFields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        // handle data
        Arrays.stream(downloader.getDestinationFile().listFiles()).forEach(file->{
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().skip(1).parallel().forEach(line -> {
                    String[] fields = line.split("\t");
                    String patent = fields[0];
                    String wipo = fields[1];
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
                            queryStream.ingest(Arrays.asList(patent,wipoTechnology));
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
