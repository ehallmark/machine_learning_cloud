package seeding.google.postgres;

import com.opencsv.CSVReader;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Iterator;

public class IngestPTAResearchDatasets {
    private static void handleFile(File file) throws Exception {
        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(file)));
        Iterator<String[]> iterator = reader.iterator();
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into big_query_pta (application_number_formatted,term_adjustments) values (?,?) on conflict (application_number_formatted) do update set term_adjustments=excluded.term_adjustments");
        long cnt = 0L;
        long total = 0L;
        iterator.next(); // first line
        while(iterator.hasNext()) {
            total ++;
            String[] line = iterator.next();
            if(line!=null && line.length>=5) {
                String appNum = line[0];
                if(appNum.length()<7) continue;
                while(appNum.length()<8) appNum="0"+appNum;
                try {
                    int pta = Integer.valueOf(line[4].trim());
                    synchronized (conn) {
                        ps.setInt(2, pta);
                        ps.setString(1, appNum);
                        ps.executeUpdate();
                    }
                    System.out.println("Seen ptas for "+file.getName()+": "+cnt);

                    if(cnt%10000==9999) {
                        conn.commit();
                        System.out.println("Seen ptas for "+file.getName()+": "+cnt);
                    }
                    cnt++;
                }catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        conn.commit();
        System.out.println("Found "+cnt+" / "+total + " total");

        reader.close();
    }




    public static void main(String[] args) throws Exception {
        File[] files = new File[]{
                new File("pta_summary2014.csv"),
                new File("pta_summary2015.csv"),
                new File("pta_summary2016.csv")
        };

        for(File file : files) {
            handleFile(file);
        }

    }
}
