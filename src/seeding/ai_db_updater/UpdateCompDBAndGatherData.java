package seeding.ai_db_updater;

import seeding.Database;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateCompDBAndGatherData {
    public static void update() {
        Database.main(null);
        try {
            Database.loadCompDBData();
        } catch(Exception e) {
            e.printStackTrace();
        }
        Database.main(null);
    }

    public static void main(String[] args) {
        update();
    }
}
