package seeding;

/**
 * Created by ehallmark on 7/21/16.
 */
public class RandomizeTestingDataByRatio {
    // RATIO HARD CODED AT 10%
    public RandomizeTestingDataByRatio() throws Exception {
        Database.updateTestingData();
    }

    public static void main(String[] args) {
        try {
            Database.setupMainConn();
            new RandomizeTestingDataByRatio();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }

}
