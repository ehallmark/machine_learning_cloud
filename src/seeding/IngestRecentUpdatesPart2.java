package seeding;


import seeding.ai_db_updater.UpdateCompDBAndGatherData;

/**
 * Created by Evan on 9/29/2017.
 */
public class IngestRecentUpdatesPart2 {
    // Completes the initial seed into mongo
    public static void main(String[] args) {
        // PRE DATA
        UpdateCompDBAndGatherData.update();


        System.out.println("Updates completed successfully.");
    }
    
}
