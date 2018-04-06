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

    private Function2<PreparedStatement,T,Void> applier;
    private AtomicLong cnt = new AtomicLong(0L);
    private PreparedStatement[] statementQueue;
    private Lock[] statementLocks;
    private Random rand = new Random(2352);
    public QueryStream(String sql, Connection conn, Function2<PreparedStatement, T, Void> applier) throws SQLException {
        this.applier=applier;
        int channels = 20;
        this.statementQueue = new PreparedStatement[channels];
        this.statementLocks = new Lock[channels];
        for(int i = 0; i < channels; i++) {
            statementQueue[i] = conn.prepareStatement(sql);
            statementLocks[i] = new ReentrantLock();
        }
    }


    public void ingest(T data) throws SQLException {
        int idx = rand.nextInt(statementLocks.length);
        Lock lock = statementLocks[idx];
        lock.lock();
        try {
            final PreparedStatement preparedStatement = statementQueue[idx];
            applier.apply(preparedStatement, data);
            preparedStatement.executeUpdate();

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
        for(PreparedStatement preparedStatement : statementQueue) {
            preparedStatement.close();
        }
        Database.commit();
    }

}
