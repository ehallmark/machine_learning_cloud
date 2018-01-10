package models.assignee.database;

import seeding.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * Created by ehallmark on 1/10/18.
 *
 * create table if not exists assignees (
        name text primary key,
        normalized_name text,
        city text,
        state text,
        country text,
        role text,
        human boolean,
        execution_date date
    );
 */
public class Seed {
    private static final int QUEUE_SIZE = 100;

    private static final List<Assignee> updateQueue = Collections.synchronizedList(new ArrayList<>(QUEUE_SIZE));

    private static Date toSQLDate(LocalDate date) {
        if (date == null) return null;
        return Date.valueOf(date);
    }


    private static String prepareInsertStatement(int size) {
        String pref = "insert into assignees (name,normalized_name,city,state,country,role,human,execution_date) values ";
        String suff = " on conflict(name) do update set (normalized_name,city,state,country,role,human,execution_date)=(?,?,?,?,?,?,?);";
        StringJoiner s = new StringJoiner(", ",pref,suff);
        for(int i = 0; i < size; i++) {
            s.add("(?,?,?,?,?,?,?,?)");
        }
        return s.toString();
    }

    private static void addToQueue(Connection conn, String name, String normalizedName, String city, String state, String country, String role, boolean human, LocalDate date) throws SQLException {
        synchronized (updateQueue) {
            if(updateQueue.size()>=QUEUE_SIZE) {
                flush(conn);
            }
        }
        Assignee assignee = new Assignee(name,normalizedName,city,state,country,role,human,date);
        synchronized (updateQueue) {
            updateQueue.add(assignee);
        }
    }

    private static void flush(Connection conn) throws SQLException {
        synchronized (updateQueue) {
            PreparedStatement ps = conn.prepareStatement(prepareInsertStatement(updateQueue.size()));
            for(int i = 0; i < updateQueue.size(); i++) {
                int j = (i*8);
                Assignee a = updateQueue.get(i);
                ps.setString(j+1, a.name);
                ps.setString(j+2, a.normalizedName);
                ps.setString(j+3, a.city);
                ps.setString(j+4, a.state);
                ps.setString(j+5, a.country);
                ps.setString(j+6, a.role);
                ps.setBoolean(j+7, a.human);
                ps.setDate(j+8, toSQLDate(a.date));
            }
            ps.executeQuery();
            updateQueue.clear();
        }
    }


    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);


        flush(conn);
        conn.commit();
        conn.close();
    }

}

