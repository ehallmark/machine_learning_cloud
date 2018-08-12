package seeding.google.postgres;

import org.nd4j.linalg.primitives.Triple;
import seeding.Database;
import seeding.ai_db_updater.handlers.MaintenanceEventHandler;
import seeding.data_downloader.MaintenanceFeeDataDownloader;
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

        final String[] maintenanceFields = new String[]{
                SeedingConstants.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY,
                SeedingConstants.ENTITY_STATUS,
                SeedingConstants.LAPSED,
                SeedingConstants.REINSTATED
        };

        final String[] maintenanceCodeFields = new String[]{
                SeedingConstants.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY,
                SeedingConstants.EVENT_CODE
        };

        // update latest assignees
        System.out.println("Starting to update latest maintenance fee data...");
        MaintenanceFeeDataDownloader downloader = new MaintenanceFeeDataDownloader();
        downloader.pullMostRecentData();
        System.out.println("Starting to ingest data...");

        String maintenanceSql = "insert into big_query_maintenance (application_number_formatted_with_country,original_entity_status,lapsed,reinstated) values (?,?,?::boolean,?::boolean) on conflict (application_number_formatted_with_country) do update set (original_entity_status,lapsed,reinstated) = (?,?::boolean,?::boolean);";
        String maintenanceCodesSql = "insert into big_query_maintenance_codes (application_number_formatted_with_country,code) values (?,?) on conflict (application_number_formatted_with_country,code) do nothing;";

        DefaultApplier maintenanceApplier = new DefaultApplier(true, conn, maintenanceFields);
        QueryStream<List<Object>> maintenanceQueryStream = new QueryStream<>(maintenanceSql,conn,maintenanceApplier);

        DefaultApplier maintenanceCodeApplier = new DefaultApplier(false, conn, maintenanceCodeFields);
        QueryStream<List<Object>> maintenanceCodeQueryStream = new QueryStream<>(maintenanceCodesSql,conn,maintenanceCodeApplier);

        Set<String> lapsed = Collections.synchronizedSet(new HashSet<>());
        Set<String> reinstated = Collections.synchronizedSet(new HashSet<>());
        Set<String> small = Collections.synchronizedSet(new HashSet<>());
        Set<String> micro = Collections.synchronizedSet(new HashSet<>());
        Set<String> large = Collections.synchronizedSet(new HashSet<>());

        Set<String> filings = Collections.synchronizedSet(new HashSet<>());

        Consumer<Triple<String,String,String>> postgresConsumer = pair -> {
            String filing = pair.getFirst();
            String entityStatus = pair.getSecond().toUpperCase().trim();
            filings.add(filing);
            String maintenanceCode = pair.getThird();
            if (entityStatus.equals("EXP.")) {
                lapsed.add(filing);
                reinstated.remove(filing);

            } else if (maintenanceCode.equals("EXPX")) {
                // reinstated
                lapsed.remove(filing);
                reinstated.add(filing);

            } else if (maintenanceCode.equals("REM.")) {
                // reminder
            }
            if (entityStatus.equals("Y")) {
                small.add(filing);
                micro.remove(filing);
                large.remove(filing);
            } else if (maintenanceCode.startsWith("N")) {
                large.add(filing);
                small.remove(filing);
                micro.remove(filing);
            } else if (maintenanceCode.startsWith("M")) {
                micro.add(filing);
                small.remove(filing);
                large.remove(filing);
            }
            try {
                maintenanceCodeQueryStream.ingest(Arrays.asList(filing, maintenanceCode));
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        ingestMaintenanceFeeData(downloader.getDestinationFile(), new MaintenanceEventHandler(postgresConsumer));

        for(String filing : filings) {
            String entityStatus = null;
            if(large.contains(filing)) {
                entityStatus = "large";
            } else if(micro.contains(filing)) {
                entityStatus = "micro";
            } else if(small.contains(filing)) {
                entityStatus = "small";
            }
            boolean isLapsed = lapsed.contains(filing);
            boolean isReinstated = reinstated.contains(filing);
            maintenanceQueryStream.ingest(Arrays.asList(filing,entityStatus,isLapsed,isReinstated));
        }

        downloader.cleanUp();

        maintenanceCodeQueryStream.close();
        maintenanceQueryStream.close();
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
