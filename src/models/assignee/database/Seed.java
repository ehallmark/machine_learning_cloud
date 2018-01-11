package models.assignee.database;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import models.assignee.normalization.name_correction.NormalizeAssignees;
import org.bson.Document;
import seeding.Constants;
import seeding.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

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
        human boolean
    );
 */
public class Seed {
    private static final int QUEUE_SIZE = 100;
    private static final int NUM_FIELDS = 7;

    private static final List<Assignee> updateQueue = Collections.synchronizedList(new ArrayList<>(QUEUE_SIZE));

    private static Date toSQLDate(LocalDate date) {
        if (date == null) return null;
        return Date.valueOf(date);
    }


    private static String prepareInsertStatement(int size) {
        String qStr;
        String[] qs = new String[NUM_FIELDS];
        for(int j = 0; j < NUM_FIELDS; j++) {
            qs[j] = "?";
        }
        qStr = "("+String.join(",",qs)+")";

        String pref = "insert into assignees_raw (name,normalized_name,city,state,country,role,human) values ";
        String suff = ";";
        StringJoiner s = new StringJoiner(", ",pref,suff);
        for(int i = 0; i < size; i++) {
            s.add(qStr);
        }
        return s.toString();
    }

    private static void addToQueue(Connection conn, String name, String normalizedName, String city, String state, String country, String role, boolean human) throws SQLException {
        synchronized (updateQueue) {
            if(updateQueue.size()>=QUEUE_SIZE) {
                flush(conn);
            }
        }
        Assignee assignee = new Assignee(name,normalizedName,city,state,country,role,human);
        synchronized (updateQueue) {
            updateQueue.add(assignee);
        }
    }

    private static void flush(Connection conn) throws SQLException {
        synchronized (updateQueue) {
            PreparedStatement ps = conn.prepareStatement(prepareInsertStatement(updateQueue.size()));
            for(int i = 0; i < updateQueue.size(); i++) {
                int j = (i*NUM_FIELDS);
                Assignee a = updateQueue.get(i);
                ps.setString(j+1, a.name);
                ps.setString(j+2, a.normalizedName);
                ps.setString(j+3, a.city);
                ps.setString(j+4, a.state);
                ps.setString(j+5, a.country);
                ps.setString(j+6, a.role);
                ps.setBoolean(j+7, a.human);
            }
            ps.executeQuery();
            updateQueue.clear();
        }
    }

    private static String toStringSafe(Object in) {
        return in==null||in.toString().length()==0?null:in.toString();
    }


    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);

        NormalizeAssignees normalizer = new NormalizeAssignees();

        Consumer<Document> consumer = doc -> {
            List<Map<String,Object>> assignees = (List<Map<String,Object>>) doc.get(Constants.ASSIGNEES);
            if(assignees!=null) {
                assignees.forEach(assignee->{
                    Object name = assignee.get(Constants.ASSIGNEE);
                    Boolean isHuman = null;
                    if(name == null) {
                        if(assignee.containsKey(Constants.FIRST_NAME)&&assignee.containsKey(Constants.LAST_NAME)) {
                            name = assignee.get(Constants.LAST_NAME)+", "+assignee.get(Constants.FIRST_NAME);
                            isHuman = true;
                        }
                    } else {
                        isHuman = false;
                    }
                    if(name!=null&&name.toString().length()>0) {
                        Object city = assignee.get(Constants.CITY);
                        Object state = assignee.get(Constants.STATE);
                        Object country = assignee.get(Constants.COUNTRY);
                        Object role = assignee.get(Constants.ASSIGNEE_ROLE);

                        String nameStr = name.toString();
                        String normalizedName = normalizer.normalizedAssignee(nameStr);
                        String cityStr = toStringSafe(city);
                        String stateStr = toStringSafe(state);
                        String countryStr = toStringSafe(country);
                        String roleStr = toStringSafe(role);

                        try {
                            addToQueue(conn, nameStr, normalizedName, cityStr, stateStr, countryStr, roleStr, isHuman);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };

        String[] fields = new String[]{
                Constants.ASSIGNEES
        };


        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,new Document(), DataIngester.TYPE_NAME,fields);

        flush(conn);
        conn.commit();
        conn.close();
    }

}

