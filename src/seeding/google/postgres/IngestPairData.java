package seeding.google.postgres;

import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.FileIterator;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.ai_db_updater.pair_bulk_data.PAIRHandler;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;
import seeding.data_downloader.PAIRDataDownloader;
import seeding.data_downloader.SingleFileDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.AssignmentHandler;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Created by Evan on 1/22/2017.
 */
public class IngestPairData {

    private static void ingestData()throws Exception {
        final Connection conn = Database.getConn();

        final String[] fields = new String[]{
                SeedingConstants.APPLICATION_NUMBER_FORMATTED,
                SeedingConstants.PUBLICATION_NUMBER,
                SeedingConstants.ENTITY_STATUS,
                SeedingConstants.APPLICATION_STATUS,
                SeedingConstants.STATUS_DATE,
                SeedingConstants.LAPSED,
                SeedingConstants.TERM_ADJUSTMENTS
        };

        Set<String> arrayFields = new HashSet<>();
        Set<String> booleanFields = new HashSet<>();
        booleanFields.add(SeedingConstants.LAPSED);

        final String valueStr = Util.getValueStrFor(fields,arrayFields,booleanFields);
        final String conflictStr = Util.getValueStrFor(IntStream.range(1,fields.length).mapToObj(i->fields[i]).toArray(s->new String[s]),arrayFields,booleanFields);
        final String sql = "insert into big_query_pair (application_number_formatted,publication_number,original_entity_type,status,status_date,abandoned,term_adjustments) values "+valueStr+" on conflict (application_number_formatted) do update set (publication_number,original_entity_type,status,status_date,abandoned,term_adjustments) = "+conflictStr+" where big_query_pair.status_date is null OR big_query_pair.status_date<excluded.status_date;";

        DefaultApplier applier = new DefaultApplier(true, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        Consumer<Map<String,Object>> ingest = map -> {
            // handle pair
            List<Object> data = new ArrayList<>();
            String filing = (String)map.get(seeding.Constants.FILING_NAME);
            String publication = (String)map.getOrDefault(seeding.Constants.GRANT_NAME, (String)map.get(seeding.Constants.PUBLICATION_NAME));
            String status = (String)map.get(seeding.Constants.APPLICATION_STATUS);
            String statusDate = (String)map.get(seeding.Constants.APPLICATION_STATUS_DATE);
            Boolean abandoned = status==null?null:(status.toLowerCase().contains("abandoned") && !status.toLowerCase().contains("restored"));
            String patentTermAdjustments = (String)map.get(seeding.Constants.PATENT_TERM_ADJUSTMENT);
            Integer patentTermAdjustmentsInt;
            if(patentTermAdjustments!=null&&patentTermAdjustments.length()>0) {
                patentTermAdjustmentsInt = Integer.valueOf(patentTermAdjustments);
            } else {
                patentTermAdjustmentsInt = null;
            }
            String entityType = (String)map.get(seeding.Constants.ASSIGNEE_ENTITY_TYPE);
            if(entityType!=null) {
                if(entityType.equalsIgnoreCase("undiscounted")) {
                    entityType = "large";
                } else {
                    entityType = entityType.toLowerCase();
                }
            }

            if(filing==null) {
                return;
            }
            data.add(filing);
            data.add(publication);
            data.add(entityType);
            data.add(status);
            data.add(statusDate);
            data.add(abandoned);
            data.add(patentTermAdjustmentsInt);
            try {
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        PAIRDataDownloader downloader = new PAIRDataDownloader();
        if(!downloader.getDestinationFile().exists()) throw new RuntimeException("Please run DownloadLatestPAIR... No PAIR data folder found at "+downloader.getDestinationFile().getAbsolutePath());
        FileIterator pairIterator = new FileIterator(downloader.getDestinationFile(),(dir, name) -> {
            try {
                return Integer.valueOf(name.substring(0,4)) >= LocalDate.now().minusYears(30).getYear();
            } catch(Exception e) {
                return false;
            }
        });
        PAIRHandler handler = new PAIRHandler(ingest,false,false);
        handler.init();
        pairIterator.applyHandlers(handler);
        handler.save();
        queryStream.close();

        downloader.cleanUp();
        try {
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }
}
