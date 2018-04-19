package seeding.google.postgres;

import pdf.PDFExtractor;
import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.FileStreamDataDownloader;
import seeding.data_downloader.PTABDataDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.PTABHandler;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestPTABData {

    private static void ingestData()throws Exception {
        final Connection conn = Database.getConn();

        final String[] fields = new String[]{
                "appeal_no",
                "interference_no",
                "patent_no",
                "pre_grant_publication_no",
                "application_no",
                "mailed_date",
                "inventor_last_name",
                "inventor_first_name",
                "case_name",
                "last_modified",
                "doc_type",
                "status",
                "image_id",
                "doc_text"
        };

        final String sql = "insert into big_query_ptab (appeal_no,interference_no,patent_no,pre_grant_publication_no,application_no,mailed_date,inventor_last_name,inventor_first_name,case_name,last_modified,doc_type,status,image_id,doc_text) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict (appeal_no,interference_no) do update set (patent_no,pre_grant_publication_no,application_no,mailed_date,inventor_last_name,inventor_first_name,case_name,last_modified,doc_type,status,image_id,doc_text) = (excluded.patent_no,excluded.pre_grant_publication_no,excluded.application_no,excluded.mailed_date,excluded.inventor_last_name,excluded.inventor_first_name,excluded.case_name,excluded.last_modified,excluded.doc_type,excluded.status,excluded.image_id,excluded.doc_text)";
        DefaultApplier applier = new DefaultApplier(false, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        // download most recent files and ingest
        FileStreamDataDownloader downloader = new PTABDataDownloader();

        Function<File,File> destinationToFileFunction = destFolder -> {
            for(File child : destFolder.listFiles()) {
                if(child.getName().startsWith("Meta_")) {
                    return child;
                }
            }
            System.out.println("Unable to find child within: "+destFolder.getAbsolutePath());
            return null;
        };


        ZipFileIterator iterator = new ZipFileIterator(downloader, "ptab_temp", false, false, file->false, false, destinationToFileFunction);


        // main consumer
        Consumer<Map<String,Object>> ingest = map -> {
            List<Object> data = Stream.of(fields).map(field->map.get(field)).collect(Collectors.toCollection(ArrayList::new));
            File file = iterator.getCurrentlyIngestingFile();
            String fileId = (String)map.get("image_id");
            String pdf = null;
            if(file!=null&&fileId!=null) {
                try {
                    pdf = PDFExtractor.extractPDF(new File(new File(file, "PDF_image"), fileId));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error while extrating pdf...");
                }
            }
            data.add(pdf);

            // get pdf text
            try {
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("During ingesting data");
                System.exit(1);
            }
        };


        NestedHandler handler = new PTABHandler(ingest);
        handler.init();
        iterator.applyHandlers(handler);
        queryStream.close();
        conn.close();
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }
}
