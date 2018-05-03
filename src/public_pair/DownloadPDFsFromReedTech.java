package public_pair;

import graphical_modeling.util.Pair;
import org.apache.commons.io.FileUtils;
import seeding.ai_db_updater.tools.ZipHelper;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadPDFsFromReedTech {
    public static final String STORAGE_PREFIX = "/usb/public_pair/";
    public static final String INDEX_FILE_URL = "http://patents.reedtech.com/downloads/PAIRIndex/Today/PAIRIndex.zip";
    public static final File PAIR_BULK_FOLDER = new File(STORAGE_PREFIX+"data/");
    public static final String INDEX_OUTPUT_FILE = STORAGE_PREFIX+"PAIRIndex.txt";
    public static final String PAIR_URL = "http://patents.reedtech.com/downloads/pairdownload/";
    public static void main(String[] args) throws Exception {
        List<Pair<String,Integer>> applicationNumbers = new ArrayList<>();
        // pull latest index file from reedtech.com
        {
            URL url = new URL(INDEX_FILE_URL);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(INDEX_OUTPUT_FILE));

            ZipHelper.unzip(url.openStream(),bos);

            bos.flush();
            bos.close();

            BufferedReader reader = new BufferedReader(new FileReader(INDEX_OUTPUT_FILE));

            AtomicLong cnt = new AtomicLong(0);
            reader.lines().forEach(line->{
                String[] cell = line.split(",");
                applicationNumbers.add(new Pair<>(cell[0],Integer.valueOf(cell[2])));
                if(cnt.getAndIncrement()%10000==9999) {
                    System.out.println("Read index: "+cnt.get());
                }
            });

            reader.close();

            System.out.println("Total: "+applicationNumbers.size());
        }

        // go through data folder and ingest any files that are missing
        final AtomicLong cnt = new AtomicLong(0);
        applicationNumbers.parallelStream().forEach(p->{
            String appNum = p._1;
            int bytes = p._2;
            File file = fileFromApplicationNumber(appNum);
            if(cnt.getAndIncrement()%1000==999) {
                System.out.println("Finished files: "+cnt.get());
            }
            if(!file.exists()||file.length()<bytes) {
                final String urlStr = PAIR_URL + appNum + ".zip";
                boolean complete = false;
                try {
                    URL url = new URL(urlStr);
                    FileUtils.copyURLToFile(url, file);
                    if(file.length()!=bytes) {
                        System.out.println("Warning incorrect file size: "+file.length()+" != "+bytes);
                    }
                    complete = true;
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("Error on url: "+urlStr);
                } finally {
                    if(!complete) {
                        // delete
                        System.out.println("Cleaning up partial: "+file.getAbsolutePath());
                        try {
                            boolean deleted = file.delete();
                            if(!deleted) {
                                System.out.println("Unable to delete on exit: "+file.getAbsolutePath());
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private static File fileFromApplicationNumber(String appNum8DigitFormat) {
        String group1 = appNum8DigitFormat.substring(0,2);
        String group2 = appNum8DigitFormat.substring(2,4);
        return fileFromHelper(group1,group2,appNum8DigitFormat);
    }

    private static File fileFromHelper(String groupNo, String groupNo2, String appNo) {
        File groupFolder = new File(new File(PAIR_BULK_FOLDER, groupNo), groupNo2);
        if(!groupFolder.exists()) {
            groupFolder.mkdirs();
        }
        return new File(groupFolder, appNo+".zip");
    }

}
