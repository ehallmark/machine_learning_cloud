package public_pair;

import graphical_modeling.util.Pair;
import org.apache.commons.io.FileUtils;
import seeding.ai_db_updater.tools.ZipHelper;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class LoadIndexFile {
    // TODO run this query periodically
    // \copy (select distinct p.application_number_formatted from big_query_pair as p join patents_global_merged as g on ('US'||p.application_number_formatted=g.application_number_formatted_with_country) where not lapsed and not abandoned and coalesce(priority_date,filing_date)>=current_date-interval '20 years' and status_date>=current_date-interval '20 years' and country_code='US') to /usb/data/valid_application_numbers_for_public_pair.csv delimiter ',' csv

    public static final String STORAGE_PREFIX = "public_pair/";
    public static final String INDEX_FILE_URL = "http://patents.reedtech.com/downloads/PAIRIndex/Today/PAIRIndex.zip";
    public static final String INDEX_OUTPUT_FILE = STORAGE_PREFIX+"PAIRIndex.txt";

    public static List<Pair<String,Long>> load() throws Exception {
        List<Pair<String,Long>> applicationNumbers = new ArrayList<>();
        // pull latest index file from reedtech.com
        {
            AtomicLong bytes = new AtomicLong(0);
            URL url = new URL(INDEX_FILE_URL);
            if(!new File(STORAGE_PREFIX).exists()) {
                new File(STORAGE_PREFIX).mkdirs();
            }
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(INDEX_OUTPUT_FILE));

            ZipHelper.unzip(url.openStream(),bos);

            bos.flush();
            bos.close();

            BufferedReader reader = new BufferedReader(new FileReader(INDEX_OUTPUT_FILE));

            AtomicLong cnt = new AtomicLong(0);
            AtomicLong ttl = new AtomicLong(0);
            reader.lines().forEach(line->{
                String[] cell = line.split(",");
                String app_num = cell[0];
                long b = Long.valueOf(cell[2]);
                applicationNumbers.add(new Pair<>(app_num, b));
                cnt.getAndIncrement();
                bytes.getAndAdd(b);
                if (ttl.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Read index: " + cnt.get()+" valid out of "+ttl.get()+" seen.");
                }
            });

            reader.close();

            System.out.println("Valid: "+applicationNumbers.size());
            System.out.println("Total: "+ttl.get());
            System.out.println("Bytes: "+bytes.get());
            System.out.println("Gigabytes: "+1e-9*bytes.get());
        }
        return applicationNumbers;
    }
    public static void main(String[] args) throws Exception {
        load();
    }

}
