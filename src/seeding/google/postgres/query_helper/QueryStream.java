package seeding.google.postgres.query_helper;

import data_pipeline.helpers.Function2;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class QueryStream<T> {

    private Function2<PreparedStatement,T,Void> applier;
    private AtomicLong cnt = new AtomicLong(0L);
    private ArrayBlockingQueue<PreparedStatement> statementQueue;
    public QueryStream(String sql, Connection conn, Function2<PreparedStatement, T, Void> applier) throws SQLException {
        this.applier=applier;
        int channels = 10;
        this.statementQueue = new ArrayBlockingQueue<>(channels);
        for(int i = 0; i < channels; i++) {
            statementQueue.add(conn.prepareStatement(sql));
        }
    }


    public void ingest(T data) throws SQLException {
        final PreparedStatement preparedStatement = statementQueue.poll();
        applier.apply(preparedStatement,data);
        preparedStatement.executeUpdate();
        if(cnt.getAndIncrement()%10000==9999) {
            System.out.println("Ingested: "+cnt.get());
            Database.commit();
        }
        try {
            statementQueue.put(preparedStatement);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while adding prepared statement back to queue.");
            System.exit(1);
        }
    }

    public synchronized void close() throws SQLException {
        for(PreparedStatement preparedStatement : statementQueue) {
            preparedStatement.close();
        }
        Database.commit();
    }

}
