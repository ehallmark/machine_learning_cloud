package seeding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SeedValuablePatentsData {
    public SeedValuablePatentsData() throws Exception {
        ResultSet rs = Database.getValuablePatents();
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            try {
                Database.updateValuablePatents(rs.getString(1),true);
                System.out.println("Valuable: "+cnt.getAndIncrement());

            } catch (SQLException sql) {
                sql.printStackTrace();
            }
        }
        Database.commit();
        ResultSet rs2 = Database.getUnValuablePatents();
        cnt.set(0);
        while(rs2.next()) {
            try {
                Database.updateValuablePatents(rs2.getString(1),false);
                System.out.println("Unvaluable: "+cnt.getAndIncrement());

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
