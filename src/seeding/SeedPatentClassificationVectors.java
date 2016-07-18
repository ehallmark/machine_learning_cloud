package seeding;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentClassificationVectors {
    private List<String> classifications;

    public SeedPatentClassificationVectors(int startDate) throws Exception {
        Database.setupSeedConn();
        classifications = Database.getDistinctClassifications();
        System.out.println("Number of classifications: "+classifications.size());

        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        while(patentNumbers.next()) {
            ResultSet rs = Database.getClassificationsFromPatents(patentNumbers.getArray(1));
            while (rs.next()) {
                String patent = rs.getString(1);
                double[] classCodes = new double[classifications.size()];
                Arrays.fill(classCodes, 0.0);
                String[] classes = (String[]) rs.getArray(2).getArray();
                if (classes != null) {
                    for (String klass : classes) {
                        classCodes[classifications.indexOf(klass)] = 1.0 / classes.length;
                    }
                }
                System.out.println(patent);
                // Update patent classification vector
            }
        }

        Database.close();
    }

    public static void main(String[] args) {
        try {
            new SeedPatentClassificationVectors(20050000);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
