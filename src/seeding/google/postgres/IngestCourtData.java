package seeding.google.postgres;

import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.jsoup.Jsoup;
import pdf.PDFExtractor;
import project_box.PGDumpLatest;
import seeding.Database;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.PTABDataDownloader;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;
import seeding.google.postgres.xml.PTABHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
                "absolute_url",
                "case_name",
                "plaintiff",
                "defendant",
                "court_id",
                "case_text",
                "patents"
        };

        final String sql = "insert into big_query_litigation (absolute_url,case_name,plaintiff,defendant,court_id,case_text,patents) values (?,?,?,?,?,?,?) on conflict (absolute_url) do update set (case_name,plaintiff,defendant,court_id,case_text,patents) = (excluded.case_name,excluded.plaintiff,excluded.defendant,excluded.court_id,excluded.case_text,excluded.patents)";
        DefaultApplier applier = new DefaultApplier(false, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        final String[] possibleMatches = new String[]{
                "us patent no",
                "us patent number",
                "us patent num",
                "u.s. patent no",
                "u.s. patent no.",
                "us patent no.",
                "u.s. patent number",
                "u.s. patent num."
        };

        AtomicLong totalCount = new AtomicLong(0);
        AtomicLong validCount = new AtomicLong(0);

        // main consumer
        Consumer<Map<String,Object>> ingest = map -> {
            totalCount.getAndIncrement();
            if(totalCount.get()%1000==999) {
                System.out.println("Finished objects: "+totalCount.get());
            }
            List<Object> data = new ArrayList<>();
            //System.out.println("Map: "+String.join("; ",map.entrySet()
            //.stream().map(e->e.getKey()+": "+e.getValue()).collect(Collectors.toList())));

            String text = ((String) map.get("html_lawbox")).toLowerCase();
            String case_name = (String) map.get("absolute_url");
            if(case_name.length()>0) {
                case_name = case_name.substring(0,case_name.length()-1);
                case_name = case_name.substring(case_name.lastIndexOf("/") + 1);
            }
            String[] case_parts = case_name.split("-v-");
            if(case_parts.length==2) {
                case_name = case_name.replace("-"," ").toUpperCase();
                String plaintiff = case_parts[0].toUpperCase().replace("-"," ").trim();
                String defendant = case_parts[1].toUpperCase().replace("-"," ").trim();
                if (Stream.of(possibleMatches).anyMatch(match -> text.contains(match))) {
                    //System.out.println("FOUND PATENT CASE: " + case_name);
                    int idx = Stream.of(possibleMatches).mapToInt(match -> {
                        int i = text.indexOf(match);
                        if (i>=0) return i+match.length();
                        else return i;
                    }).max().orElse(-1);
                    Set<String> patents = new HashSet<>();
                    while (idx >= 0) {
                        int parenIdx = text.indexOf(" ", idx + 7);
                        if (parenIdx > 0 && parenIdx < idx + 15) {
                            int first_space = text.indexOf(" ",idx);
                            String patentNumber = text.substring(first_space, parenIdx).replace(",", "").replace(";", "").replace(".", "").trim();
                            patentNumber = patentNumber.toUpperCase().replace(" ","");
                            if (patentNumber.length() > 5 && patentNumber.length() <= 9) {
                               // System.out.println("Patent number: " + patentNumber);
                               // System.out.println("Plaintiff: " + plaintiff);
                               // System.out.println("Defendant: " + defendant);
                                patents.add(patentNumber);
                                idx = Stream.of(possibleMatches).mapToInt(match -> {
                                    int i = text.indexOf(match, parenIdx);
                                    if(i>=0) return i+match.length();
                                    else return i;
                                }).max().orElse(-1);
                            } else {
                                idx = -1;
                            }
                        } else {
                            idx = -1;
                        }
                    }
                    if(patents.size()>0) {
                        validCount.getAndIncrement();
                        if(validCount.get()%10==9)System.out.println(validCount.get());
                        data.add(map.get("absolute_url"));
                        data.add(case_name);
                        data.add(plaintiff);
                        data.add(defendant);
                        data.add(map.get("court_id"));
                        data.add(map.get("html_lawbox"));
                        data.add(patents.toArray(new String[patents.size()]));
                        try {
                            queryStream.ingest(data);
                        } catch(Exception e) {
                            e.printStackTrace();
                            System.out.println("During ingesting data");
                            System.exit(1);
                        }
                    }
                }
            }

        };


        File dataFile = new File("/usb/temp_ingest_courts_data/");
        for(File tarGzFile : new File[]{new File("/usb/data/all_courts_data/vaed.tar.gz")}) {//tarDataFolder.listFiles()) {
            if(dataFile.exists()) {
                if(dataFile.isFile()) {
                    dataFile.delete();
                } else {
                    FileUtils.deleteDirectory(dataFile);
                }
            }
            System.out.print("Starting to decompress: "+tarGzFile.getAbsolutePath()+"...");
            decompressFolderToSeparateLocation(tarGzFile,dataFile);
            System.out.println(" Done.");
            // ingest dataFile
            for(File file : dataFile.listFiles()) {
                Map<String,Object> doc = Document.parse(bufferedReaderFileToString(file));
                doc.put("court_id",tarGzFile.getName().split("\\.")[0]);
                ingest.accept(doc);

            }
            System.out.println("Completed: "+dataFile.listFiles().length);
        }


        queryStream.close();
        conn.close();
    }

    private static String bufferedReaderFileToString(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return String.join("", reader.lines().collect(Collectors.toList()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
