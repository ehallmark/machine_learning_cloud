package seeding.google.postgres.query_helper;

import data_pipeline.helpers.Function2;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class QueryStream<T> {

    private Function2<PreparedStatement,T,Boolean> applier;
    private AtomicLong cnt = new AtomicLong(0L);
    private PreparedStatement preparedStatement;
    private Lock lock;
    public QueryStream(String sql, Connection conn, Function2<PreparedStatement, T, Boolean> applier) throws SQLException {
        this.applier=applier;
        if(sql != null) preparedStatement = conn.prepareStatement(sql);
        lock = new ReentrantLock();
    }

    public void ingest(T data) throws SQLException {
        ingest(data, lock, preparedStatement);
    }

    public void ingest(T data, Lock lock, PreparedStatement preparedStatement) throws SQLException {
        if(lock!=null) lock.lock();
        try {
            if(applier.apply(preparedStatement, data)) {
               // System.out.println("Prepared statement: "+ preparedStatement.toString());
                preparedStatement.executeUpdate();
            }
            preparedStatement.clearParameters();


        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while adding prepared statement back to queue.");
            System.exit(1);
        } finally {
            if(lock!=null) lock.unlock();
        }
        if(cnt.getAndIncrement()%10000==9999) {
            System.out.println("Ingested: "+cnt.get());
            Database.commit();
        }
    }

    public void close() throws SQLException {
        preparedStatement.close();
        Database.commit();
    }

}
