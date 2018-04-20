package seeding.google.postgres;

import pdf.PDFExtractor;
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
public class IngestPTABData {
    private static final boolean testing = false;

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

        // download most recent files and ingest
        PTABDataDownloader downloader = new PTABDataDownloader();

        Function<File,List<File>> destinationToFileFunction = destFolder -> {
            if(destFolder.getName().endsWith(downloader.getBackFile().getName())) {
                System.out.println("Found backfile... "+destFolder.getAbsolutePath());
                return Arrays.asList(new File(destFolder,"EFOIA").listFiles((file)->file.getName().startsWith("PTAB"))[0]
                        .listFiles(file->file.getName().endsWith(".xml")));
            } else {
                for (File child : destFolder.listFiles()[0].listFiles()) {
                    if (child.getName().startsWith("Meta_")) {
                        return Collections.singletonList(child);
                    }
                }
            }
            System.out.println("Unable to find child within: "+destFolder.getAbsolutePath());
            return null;
        };


        ZipFileIterator iterator = new ZipFileIterator(downloader, "ptab_temp", false, false, file->false, testing, destinationToFileFunction);

        // main consumer
        Consumer<Map<String,Object>> ingest = map -> {
            if(map.containsKey("mailed_date")) {
                map.put("mailed_date", map.get("mailed_date").toString().split(" ")[0].replace("/","-"));
                try {
                    LocalDate.parse(map.get("mailed_date").toString());
                } catch(Exception e) {
                    map.remove("mailed_date");
                }
            }
            if(map.containsKey("last_modified")) {
                map.put("last_modified", map.get("last_modified").toString().split(" ")[0].replace("/","-"));
                try {
                    LocalDate.parse(map.get("last_modified").toString());
                } catch(Exception e) {
                    map.remove("last_modified");
                }
            }
            if(!map.containsKey("image_id")) return; // no pdf
            map.put("image_id",map.get("image_id").toString().replace(" ",""));
            List<Object> data = Stream.of(fields).map(field->map.get(field)).collect(Collectors.toCollection(ArrayList::new));
            File file = iterator.getCurrentlyIngestingFile();
            String fileId = (String)map.get("image_id");
            String pdf = null;
            String type = (String)map.get("doc_type");
            String year;
            if(map.containsKey("mailed_date")) {
                year = map.get("mailed_date").toString().substring(0,4);
            } else {
                try {
                    year = fileId.split("-")[3];
                    if(year.length()!=4) {
                        year = null;
                    }
                } catch(Exception e) {
                    //e.printStackTrace();
                    year=null;
                }
            }
            if(file!=null&&type!=null) {
                fileId = fileId.replace(" ","");
                file = file.getParentFile();
                File potentialBackfile = file==null?null:file.getParentFile();
                boolean isBackfile = potentialBackfile!=null && potentialBackfile.getName().startsWith("EFOIA");
                File pdfFile;
                if(isBackfile) {
                    System.out.println("Found backfile!");
                    pdfFile = new File(new File(new File(file,type), year), fileId+".pdf");
                } else {
                    System.out.println("Not a backfile.");
                    //System.out.println("Backfile: "+downloader.getBackFile().getAbsolutePath());
                    //System.out.println("Attempt to find backfile: "+potentialBackfile.getAbsolutePath());
                    pdfFile = new File(new File(file, "PDF_image"), fileId+".pdf");
                }
                try {
                    pdf = PDFExtractor.extractPDF(pdfFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error while extracting pdf...");
                }
            }
            data.set(data.size()-1,pdf);

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
