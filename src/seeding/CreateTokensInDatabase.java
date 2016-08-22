package seeding;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/22/16.
 */
public class CreateTokensInDatabase {

    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Database.setupMainConn();
        ResultSet rs = Database.selectRawPatentNumbers();
        AtomicInteger cnt = new AtomicInteger(0);
        long lastTime = System.currentTimeMillis();
        while(rs.next()) {
            Database.createWordVectorsInDB(rs.getString(1));
            int count = cnt.getAndIncrement();
            if(count >= 999) {
                Database.commit();
                System.out.println("Time to commit 1000 patents: " + new Double(System.currentTimeMillis()-lastTime) / 1000 + "seconds");
                lastTime = System.currentTimeMillis();
                cnt.set(0);
            }
        }
        Database.commit();
        Database.close();
    }
}
