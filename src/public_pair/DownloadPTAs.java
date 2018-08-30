package public_pair;

import graphical_modeling.util.Pair;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import seeding.Database;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DownloadPTAs {
    public static void main(String[] args) throws Exception {
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select application_number_formatted from big_query_pair where term_adjustments is null");
        ps.setFetchSize(100);
        Set<String> emptyApps = new HashSet<>();
        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            emptyApps.add(rs.getString(1));
        }
        rs.close();
        ps.close();

        // test! Set<String> emptyApps = new HashSet<>(Collections.singleton("09640035"));

        System.out.println("Found "+emptyApps.size()+" applications without term adjustments.");
        List<Pair<String,Long>> applicationNumbers = new ArrayList<>(LoadIndexFile.load());
        Collections.shuffle(applicationNumbers);

        final String baseUrl = "http://patents.reedtech.com/downloads/pairdownload/{{PATENT}}.zip";

        ExecutorService service = Executors.newFixedThreadPool(6);

        AtomicLong cnt = new AtomicLong(0);
        for(Pair<String,Long> app : applicationNumbers) {
            if(emptyApps.contains(app._1)) {
                service.execute(() -> {
                    String url = baseUrl.replace("{{PATENT}}", app._1);
                    handleUrl(url, app._1, app._2, conn);
                    if(cnt.getAndIncrement() % 1000 == 999) {
                        System.out.println("Completed: " + cnt.get());
                        try {
                            conn.commit();
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }

        service.shutdown();
        service.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        conn.commit();
        conn.close();
    }

    private static void handleUrl(String url, String appNum, long bytes, Connection conn)  {
        File file = DownloadPDFsFromReedTech.fileFromApplicationNumber(appNum);
        try {
            if (!file.exists() || file.length() < bytes) {
                FileUtils.copyURLToFile(new URL(url), file);
                if (file.length() != bytes) {
                    System.out.println("Warning incorrect file size: " + file.length() + " != " + bytes);
                }
            }

            // unzip
            ZipFile zip = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if(entry.getName().contains("patent_term_adjustments")) {
                    System.out.println("FOUND TERM ADJUSTMENTS!!!   Application: "+appNum);
                    InputStream stream = zip.getInputStream(entry);
                    StringWriter writer = new StringWriter();
                    IOUtils.copy(stream, writer, Charsets.UTF_8);
                    String string = writer.toString();
                    String[] lines = string.split("\\r?\\n");
                    boolean found = false;
                    for(String line : lines) {
                        String[] cells = line.split("\\t");
                        if(cells.length>1) {
                            if (cells[0].toLowerCase().contains("total pta adjustments")) {
                                try {
                                    int pta = Integer.valueOf(cells[1].trim().replaceAll("[^0-9]", ""));
                                    synchronized (conn) {
                                        found = true;
                                        PreparedStatement ps = conn.prepareStatement("update big_query_pair set term_adjustments = ? where application_number_formatted = ?");
                                        ps.setInt(1, pta);
                                        ps.setString(2, appNum);
                                        ps.executeUpdate();
                                    }
                                    System.out.println("Found pta: " + pta);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    System.out.println("Error parsing pta for line: " + line);
                                    System.out.println("FULL TSV: " + string);
                                }
                                break;
                            }
                        }
                    }
                    synchronized (conn) {
                        if (!found) {
                            int pta = 0;
                            PreparedStatement ps = conn.prepareStatement("update big_query_pair set term_adjustments = ? where application_number_formatted = ?");
                            ps.setInt(1, pta);
                            ps.setString(2, appNum);
                            ps.executeUpdate();
                        }
                        conn.commit();
                    }
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on application: " + appNum);
        } finally {
            // cleanup
            if(!file.delete()) {
                System.out.println("Unable to delete file: "+file.getName());
            }
        }
    }
}
