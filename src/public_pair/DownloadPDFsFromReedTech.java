package public_pair;

import graphical_modeling.util.Pair;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadPDFsFromReedTech {
    public static final File PAIR_BULK_FOLDER = new File("pair_bulk_data/");
    public static void main(String[] args) throws Exception {
        List<Pair<String,Long>> applicationNumbers = LoadIndexFile.load();
        final ProxyHandler proxyHandler = new ProxyHandler();

        // go through data folder and ingest any files that are missing
        final AtomicLong cnt = new AtomicLong(0);
        Collections.shuffle(applicationNumbers, new Random(System.currentTimeMillis()));
        final ExecutorService executor = Executors.newFixedThreadPool(32);
        applicationNumbers.forEach(p->{
            executor.execute(()-> {
                String appNum = p._1;
                long bytes = p._2;
                File file = fileFromApplicationNumber(appNum);
                //if (cnt.getAndIncrement() % 10 ==9) {
                    cnt.getAndIncrement();
                    System.out.println("Finished files: " + cnt.get());
                //}
                if (!file.exists() || file.length() < bytes) {
                    // send request to pair proxy
                    boolean complete = false;
                    try {
                        HttpURLConnection conn = proxyHandler.getProxyUrlForApplication(appNum);
                        FileUtils.copyInputStreamToFile(conn.getInputStream(), file);
                        if (file.length() != bytes) {
                            System.out.println("Warning incorrect file size: " + file.length() + " != " + bytes);
                        }
                        complete = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Error on application: " + appNum);
                    } finally {
                        if (!complete) {
                            // delete
                            System.out.println("Cleaning up partial: " + file.getAbsolutePath());
                            try {
                                boolean deleted = file.delete();
                                if (!deleted) {
                                    System.out.println("Unable to delete on exit: " + file.getAbsolutePath());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        });
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public static File fileFromApplicationNumber(String appNum8DigitFormat) {
        String group1 = appNum8DigitFormat.substring(0,2);
        String group2 = appNum8DigitFormat.substring(2,4);
        return fileFromHelper(group1,group2,appNum8DigitFormat);
    }

    private static File fileFromHelper(String groupNo, String groupNo2, String appNo) {
       // File groupFolder = new File(new File(PAIR_BULK_FOLDER, groupNo), groupNo2);
       // if(!groupFolder.exists()) {
       //     groupFolder.mkdirs();
       // }
       // return new File(groupFolder, appNo+".zip");
        return new File(PAIR_BULK_FOLDER,appNo);
    }

}
