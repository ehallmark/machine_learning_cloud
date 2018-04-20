package seeding.google.postgres;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/22/2017.
 */
public class IngestCourtData {
    private static final File tarDataFolder = new File("/home/ehallmark/data/all_courts_data/all_courts_data/");
    private static void ingestData()throws Exception {
        final Connection conn = Database.getConn();

        final String[] fields = new String[]{
                "absolute_url",
                "case_name",
                "plaintiff",
                "defendant",
                "court_id",
                "case_text",
                "patents",
                "infringement_flag"
        };

        final String sql = "insert into big_query_litigation (absolute_url,case_name,plaintiff,defendant,court_id,case_text,patents,infringement_flag) values (?,?,?,?,?,?,?,?::boolean) on conflict (absolute_url) do update set (case_name,plaintiff,defendant,court_id,case_text,patents,infringement_flag) = (excluded.case_name,excluded.plaintiff,excluded.defendant,excluded.court_id,excluded.case_text,excluded.patents,excluded.infringement_flag)";
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
            if(case_name==null) return;
            if(case_name.length()>0) {
                case_name = case_name.substring(0,case_name.length()-1);
                case_name = case_name.substring(case_name.lastIndexOf("/") + 1);
            }
            String[] case_parts = case_name.split("-v-",2);
            boolean patentInfringementFlag = text.contains("patent infringement");
            if(case_parts.length==2) {
                case_name = case_name.replace("-"," ").toUpperCase();
                String plaintiff = case_parts[0].toUpperCase().replace("-"," ").trim();
                String defendant = case_parts[1].toUpperCase().replace("-"," ").trim();
                if (patentInfringementFlag||Stream.of(possibleMatches).anyMatch(match -> text.contains(match))) {
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
                    validCount.getAndIncrement();
                    if (validCount.get() % 10 == 9) System.out.println(validCount.get());
                    data.add(map.get("absolute_url"));
                    data.add(case_name);
                    data.add(plaintiff);
                    data.add(defendant);
                    data.add(map.get("court_id"));
                    data.add(map.get("html_lawbox"));
                    if(patents.isEmpty()) data.add(null);
                    else data.add(patents.toArray(new String[patents.size()]));
                    data.add(patentInfringementFlag);
                    try {
                        queryStream.ingest(data);
                    } catch(Exception e) {
                        e.printStackTrace();
                        System.out.println("During ingesting data");
                        System.exit(1);
                    }
                }
            } else if(patentInfringementFlag) {
                //System.out.println("Could not create plaintiff/defendent pair from: "+case_name);
            }

        };


        for(File tarGzFile : tarDataFolder.listFiles((file->{
            return (file.getName().startsWith("ca")&&!file.getName().startsWith("cal"))
                    || file.getName().startsWith("scotus")
                    || file.getName().startsWith("cc")
                    || (!(file.getName().startsWith("ind.")||file.getName().startsWith("md.")||file.getName().startsWith("nd.")||file.getName().startsWith("sd."))&&file.getName().contains("d."));
        }))) {
            System.out.println("Starting to decompress: "+tarGzFile.getAbsolutePath()+"...");
            Consumer<String> fileHandler = fileStr -> {
                Map<String,Object> doc = Document.parse(fileStr);
                doc.put("court_id",tarGzFile.getName().split("\\.")[0]);
                ingest.accept(doc);
            };
            extractTarGz(tarGzFile,fileHandler);//decompressFolderToSeparateLocation(tarGzFile,dataFile);
        }


        queryStream.close();
        conn.close();
    }

    public static void main(String[] args) throws Exception {
        ingestData();
    }

    private static final int BUFFER_SIZE = 4096;
    private static void extractTarGz(File tarGz, Consumer<String> handler) throws IOException {
        try (TarArchiveInputStream inStream = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarGz))))) {
            TarArchiveEntry tarFile;
            while ((tarFile = (TarArchiveEntry) inStream.getNextEntry()) != null) {
                if (!tarFile.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int count;
                    byte data[] = new byte[BUFFER_SIZE];
                    while ((count = inStream.read(data, 0, BUFFER_SIZE)) != -1) {
                        baos.write(data,0,count);
                    }
                    handler.accept(baos.toString());
                    baos.close();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
