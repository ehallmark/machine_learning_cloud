package seeding;

import java.io.File;
import java.sql.SQLException;
import java.util.Set;

/**
 * Created by ehallmark on 5/1/17.
 */
public class LoadJapaneseCompaniesSet {
    public static final File FILE = new File(Constants.DATA_FOLDER+"japanese_companies_set.jobj");
    public static void main(String[] args) throws SQLException {
        Database.setupSeedConn();
        Set<String> japaneseCompanies = Database.loadJapaneseCompaniesSetFromDB();
        System.out.println("Num japanese companies: "+japaneseCompanies.size());
        Database.trySaveObject(japaneseCompanies,FILE);
    }
}
