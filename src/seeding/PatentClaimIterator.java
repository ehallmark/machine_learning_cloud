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
    @Override
    public String nextSentence() {
        try {
            // Check for more results in result set
            resultSet.next();
            return resultSet.getString(1);

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return (resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast()));
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }

    @Override
    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            resetQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("UNABLE TO RESET QUERY");
        }
    }

}
