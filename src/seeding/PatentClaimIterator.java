package seeding;

import java.sql.SQLException;

/**
 * Created by ehallmark on 7/29/16.
 */
public class PatentClaimIterator extends BasePatentIterator {
    public PatentClaimIterator(int startDate) throws SQLException {
        super(startDate);
    }
    protected void resetQuery() throws SQLException {
        resultSet = Database.getPatentClaimVectorData();
    }
}
