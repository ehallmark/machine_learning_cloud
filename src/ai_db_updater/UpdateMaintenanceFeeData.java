package ai_db_updater;

import ai_db_updater.database.Database;

import java.io.IOException;
import java.util.Set;

/**
 * Created by ehallmark on 1/19/17.
 */
public class UpdateMaintenanceFeeData {
    public static Set<String> load() throws IOException,ClassNotFoundException {
        return Database.loadExpiredPatentsSet();
    }
    public static void main(String[] args) {
        try {
            // update latest assignees
            System.out.println("Starting to update latest maintenace fee data...");
            Database.loadAndIngestMaintenanceFeeData();
        } catch(Exception sql) {
            sql.printStackTrace();
        }

    }
}
