package seeding.google.postgres;

import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import seeding.ai_db_updater.handlers.MaintenanceEventHandler;
import seeding.data_downloader.MaintenanceFeeDataDownloader;
import seeding.google.attributes.Constants;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by ehallmark on 1/19/17.
 */
public class IngestMaintenanceFeeData {
    private static AtomicInteger cnt = new AtomicInteger(0);
    public static void main(String[] args) throws Exception{
        Connection conn = Database.getConn();

        final String[] maintenaceFields = new String[]{
                Constants.PUBLICATION_NUMBER,
                Constants.ENTITY_STATUS,
                Constants.LAPSED,
                Constants.REINSTATED
        };

        final String[] maintenanceCodeFields = new String[]{
                Constants.PUBLICATION_NUMBER,
                Constants.EVENT_CODE
        };

        // update latest assignees
        System.out.println("Starting to update latest maintenance fee data...");
        MaintenanceFeeDataDownloader downloader = new MaintenanceFeeDataDownloader();
        downloader.pullMostRecentData();
        System.out.println("Starting to ingest data...");

        String maintenanceSql = "insert into big_query_maintenance (publication_number,original_entity_status,lapsed,reinstated) values (?,?,?::boolean,?::boolean) on conflict (publication_number) do update set (original_entity_status,lapsed,reinstated) = (?,?::boolean,?::boolean);";
        String maintenanceCodesSql = "insert into big_query_maintenance_codes (publication_number,code) values (?,?) on conflict (publication_number,code) do nothing;";

        DefaultApplier maintenanceApplier = new DefaultApplier(true, conn, maintenaceFields);
        QueryStream<List<Object>> maintenanceQueryStream = new QueryStream<>(maintenanceSql,conn,maintenanceApplier);

        DefaultApplier maintenanceCodeApplier = new DefaultApplier(false, conn, maintenanceCodeFields);
        QueryStream<List<Object>> maintenanceCodeQueryStream = new QueryStream<>(maintenanceCodesSql,conn,maintenanceCodeApplier);

        Set<String> lapsed = Collections.synchronizedSet(new HashSet<>());
        Set<String> reinstated = Collections.synchronizedSet(new HashSet<>());
        Set<String> small = Collections.synchronizedSet(new HashSet<>());
        Set<String> micro = Collections.synchronizedSet(new HashSet<>());
        Set<String> large = Collections.synchronizedSet(new HashSet<>());

        Set<String> patents = Collections.synchronizedSet(new HashSet<>());

        Consumer<Pair<String,String>> postgresConsumer = pair -> {
            String patent = pair.getFirst();
            patents.add(patent);
            String maintenanceCode = pair.getSecond();
            if (maintenanceCode.equals("EXP.")) {
                lapsed.add(patent);
                reinstated.remove(patent);

            } else if (maintenanceCode.equals("EXPX")) {
                // reinstated
                lapsed.remove(patent);
                reinstated.add(patent);

            } else if (maintenanceCode.equals("REM.")) {
                // reminder
            } else if (maintenanceCode.startsWith("M2") || maintenanceCode.startsWith("SM") || maintenanceCode.equals("LTOS") || maintenanceCode.equals("MTOS")) {
                small.add(patent);
                micro.remove(patent);
                large.remove(patent);
            } else if (maintenanceCode.startsWith("M1") || maintenanceCode.startsWith("LSM")) {
                large.add(patent);
                small.remove(patent);
                micro.remove(patent);
            } else if (maintenanceCode.startsWith("M3") || maintenanceCode.equals("STOM")) {
                micro.add(patent);
                small.remove(patent);
                large.remove(patent);
            }
            try {
           //     maintenanceCodeQueryStream.ingest(Arrays.asList(patent, maintenanceCode));
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        ingestMaintenanceFeeData(downloader.getDestinationFile(), new MaintenanceEventHandler(postgresConsumer));

        for(String patent : patents) {
            String entityStatus = null;
            if(large.contains(patent)) {
                entityStatus = "large";
            } else if(micro.contains(patent)) {
                entityStatus = "micro";
            } else if(small.contains(patent)) {
                entityStatus = "small";
            }
            boolean isLapsed = lapsed.contains(patent);
            boolean isReinstated = reinstated.contains(patent);
            maintenanceQueryStream.ingest(Arrays.asList(patent,entityStatus,isLapsed,isReinstated));
        }

        downloader.cleanUp();

        maintenanceCodeQueryStream.close();
        maintenanceQueryStream.close();
        conn.close();
    }

    public static void ingestMaintenanceFeeData(File destinationFile, MaintenanceEventHandler handler) throws Exception {
        // should be one at least every other month
        // Load file from Google
        Arrays.stream(destinationFile.listFiles()).forEach(file -> {
            if (!file.getName().endsWith(".txt")) {
                file.delete();
                return;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line->{
                    handler.handleLine(line);
                    if (cnt.getAndIncrement() % 100000 == 99999) {
                        System.out.println("Seen maintenance events: " + cnt.get());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        handler.save();
    }
}
