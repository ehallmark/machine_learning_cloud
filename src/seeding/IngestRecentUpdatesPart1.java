package seeding;

import seeding.ai_db_updater.UpdateAll;

/**
 * Created by Evan on 9/29/2017.
 */
public class IngestRecentUpdatesPart1 {
    public static void main(String[] args) {
        String[] updates = new String[]{"-1","1","2","3","4","5","6","7","8","9","10","11","12"};

        try {
            UpdateAll.main(updates);

        } catch(Exception e) {
            System.out.println("Error during seeding...");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
