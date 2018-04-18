package seeding.google.postgres;

import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AssignmentDataDownloader;
import seeding.data_downloader.FileStreamDataDownloader;
import seeding.data_downloader.PTABDataDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.AssignmentHandler;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestPTABData {

    private static void ingestData()throws Exception {
        //final Connection conn = Database.getConn();
        final String[] fields = new String[]{
                "something",
                "else"
        };
        final String sql = "TODO";

       // DefaultApplier applier = new DefaultApplier(false, conn, fields);
       // QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);


        // main consumer
        Consumer<Map<String,Object>> ingest = map -> {
            // TODO
        };


        // download most recent files and ingest
        FileStreamDataDownloader downloader = new PTABDataDownloader();
        downloader.pullMostRecentData();

       // WebIterator iterator = new ZipFileIterator(downloader, "ptab_temp", true, false, null, false);
       // NestedHandler handler = new AssignmentHandler(ingest);
       // handler.init();
       // iterator.applyHandlers(handler);
       // queryStream.close();
       // conn.close();
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }
}
