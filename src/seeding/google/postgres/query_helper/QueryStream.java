package seeding.google.postgres.query_helper;

import data_pipeline.helpers.Function2;
import seeding.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public class QueryStream<T> {

    private PreparedStatement preparedStatement;
    private Function2<PreparedStatement,T,Void> applier;
    private AtomicLong cnt = new AtomicLong(0L);
    public QueryStream(PreparedStatement preparedStatement, Function2<PreparedStatement,T,Void> applier) {
        this.preparedStatement=preparedStatement;
        this.applier=applier;
    }


    public void ingest(T data) throws SQLException {
        applier.apply(preparedStatement,data);
        preparedStatement.executeUpdate();
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
