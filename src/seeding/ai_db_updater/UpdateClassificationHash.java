package seeding.ai_db_updater;

import seeding.Database;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    public static Map<String,Set<String>> load() throws IOException,ClassNotFoundException {
        return Database.loadPatentToClassificationMap();
    }
    public static void main(String[] args) throws Exception {
        Database.setupClassificationsHash();
    }
}
