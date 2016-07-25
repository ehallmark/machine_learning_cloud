package seeding;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SeedValuablePatentsData {
    public SeedValuablePatentsData() throws Exception {
        ResultSet rs = Database.getValuablePatents();
        while(rs.next()) {
            try {
                Database.updateValuablePatents(rs.getString(1),true);
                System.out.println(rs.getString(1));

            } catch (SQLException sql) {
                sql.printStackTrace();
            }
        }
        Database.commit();
        ResultSet rs2 = Database.getUnValuablePatents();
        while(rs2.next()) {
            try {
                Database.updateValuablePatents(rs2.getString(1),false);
                System.out.println(rs.getString(1));

            } catch (SQLException sql) {
                sql.printStackTrace();
            }
        }
        Database.commit();
    }

    public static void main(String[] args) {
        try {
            Database.setupMainConn();
            Database.setupSeedConn();
            new SeedValuablePatentsData();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }

}
