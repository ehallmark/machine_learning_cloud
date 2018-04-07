package seeding.google.postgres.query_helper;

import data_pipeline.helpers.Function2;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
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
        preparedStatement = conn.prepareStatement(sql);
        lock = new ReentrantLock();
    }


    public void ingest(T data) throws SQLException {
        lock.lock();
        try {
            if(applier.apply(preparedStatement, data)) {
                preparedStatement.executeUpdate();
            }
            preparedStatement.clearParameters();


        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while adding prepared statement back to queue.");
            System.exit(1);
        } finally {
            lock.unlock();
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
