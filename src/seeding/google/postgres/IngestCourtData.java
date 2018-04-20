package seeding.google.postgres;

import org.apache.commons.io.FileUtils;
import org.bson.Document;
import pdf.PDFExtractor;
import project_box.PGDumpLatest;
import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.PTABDataDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.PTABHandler;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestCourtData {
    private static final boolean testing = true;
    private static final File tarDataFolder = new File("/usb/data/all_courts_data/");
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

        final String sql = "insert into big_query_ptab (appeal_no,interference_no,patent_no,pre_grant_publication_no,application_no,mailed_date,inventor_last_name,inventor_first_name,case_name,last_modified,doc_type,status,image_id,doc_text) values (?,?,?,?,?,?::date,?,?,?,?::date,?,?,?,?) on conflict (image_id) do update set (appeal_no,interference_no,patent_no,pre_grant_publication_no,application_no,mailed_date,inventor_last_name,inventor_first_name,case_name,last_modified,doc_type,status,doc_text) = (excluded.appeal_no,excluded.interference_no,excluded.patent_no,excluded.pre_grant_publication_no,excluded.application_no,excluded.mailed_date,excluded.inventor_last_name,excluded.inventor_first_name,excluded.case_name,excluded.last_modified,excluded.doc_type,excluded.status,excluded.doc_text)";
        DefaultApplier applier = new DefaultApplier(false, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);


        // main consumer
        Consumer<Map<String,Object>> ingest = map -> {
            List<Object> data = new ArrayList<>();
            System.out.println("Map: "+String.join("; ",map.entrySet()
            .stream().map(e->e.getKey()+": "+e.getValue()).collect(Collectors.toList())));

            if(data.size()==0) return;
            // get pdf text
            try {
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("During ingesting data");
                System.exit(1);
            }
        };


        File dataFile = new File("temp_ingest_courts_data/");
        for(File tarGzFile : tarDataFolder.listFiles()) {
            if(dataFile.exists()) {
                if(dataFile.isFile()) {
                    dataFile.delete();
                } else {
                    FileUtils.deleteDirectory(dataFile);
                }
            }
            System.out.println("Starting to decompress: "+tarGzFile.getAbsolutePath());
            decompressFolderToSeparateLocation(tarGzFile,dataFile);

            // ingest dataFile
            for(File file : dataFile.listFiles()) {
                System.out.print("-");
                ingest.accept(Document.parse(FileUtils.readFileToString(file)));
            }
            System.out.println();
        }


        queryStream.close();
        conn.close();
    }


    public static void decompressFolderToSeparateLocation(File tarDotGzFile, File destination) {
        if(!destination.exists()) destination.mkdirs();
        if(destination.isFile()) throw new IllegalStateException("Destination folder must be a folder and not a file...");
        try {
            PGDumpLatest.startProcess("tar -xzvf "+tarDotGzFile.getAbsolutePath()+" -C "+destination.getAbsolutePath());
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to decompress folder: "+tarDotGzFile.getAbsolutePath());
        }
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }
}
