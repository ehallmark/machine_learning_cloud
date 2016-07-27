package seeding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SeedValuablePatentsData { // IS VALUABLE IF IT IS A PANASONIC PATENT!!!!
    public SeedValuablePatentsData() throws Exception {
        Database.resetValuablePatents();
        Database.commit();
        ResultSet rs = Database.getValuablePatents();
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            try {
                Database.updateValuablePatents(rs.getString(1),true);
                System.out.println(cnt.getAndIncrement());

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
