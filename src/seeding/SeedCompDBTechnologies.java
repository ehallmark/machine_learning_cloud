package seeding;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/22/16.
 */
public class SeedCompDBTechnologies {

    private List<Integer> technologies;
    private final int commitLength;
    private int timeToCommit;
    private long startTime;
    private AtomicInteger count;
    private Map<String, Integer[]> technologyMap;

    public SeedCompDBTechnologies() throws Exception {
        technologies = Database.getDistinctCompDBTechnologyIds();
        System.out.println("Number of technologies: "+technologies.size());
        technologyMap = Database.getCompDBTechnologyMap();
        timeToCommit = 0;
        commitLength = 1000;
        startTime = System.currentTimeMillis();
        count = new AtomicInteger(0);

        // Get compdb patents
        handleTechnologyMapAndGenerateUpdates();
    }

    private void handleTechnologyMapAndGenerateUpdates() throws SQLException {
        technologyMap.entrySet().parallelStream().forEach(entry->{
            String patent = entry.getKey();
            Integer[] labels = entry.getValue();
            try {
                updatePatentWithTechnologies(patent,labels);
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        Database.commit();
    }

    private void updatePatentWithTechnologies(String pubDocNumber, Integer[] labels) throws SQLException {
        if(pubDocNumber==null)return;

        // Update patent
        Double[] classSoftMax = computeSoftMax(labels);

        if(classSoftMax!=null) {
            try {
                Database.updateCompDBTechnologies(pubDocNumber, classSoftMax);
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
            if (timeToCommit % commitLength == 0) {
                Database.commit();
                long endTime = System.currentTimeMillis();
                System.out.println("Seconds to complete 1000 patents: "+new Double(endTime-startTime)/1000);
                startTime = endTime;
            }
            timeToCommit = (timeToCommit + 1) % commitLength;
            System.out.println(count.getAndIncrement());

        } else {
            System.out.println("-");
        }
    }

    private Double[] computeSoftMax(Integer[] classes) {
        Double[] classSoftMax = new Double[technologies.size()];
        Arrays.fill(classSoftMax, 0.0);
        if (classes != null) {
            for (Integer techId : classes) {
                int index = technologies.indexOf(techId);
                if(index>=0)classSoftMax[index] = 1.0;
            }
            return classSoftMax;
        } else {
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            Database.setupCompDBConn();
            new SeedCompDBTechnologies();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
