package models.assignee.database;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.EntityTypeAttribute;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Created by ehallmark on 1/10/18.
 *
 * create table if not exists assignees (
        name text primary key,
        city text,
        state text,
        country text,
        role text,
        human boolean
    );
 */
public class Seed {
    private static final int QUEUE_SIZE = 100;
    public static final int NUM_FIELDS = 7;
    private static final int COMMIT_N_BATCHES = 100;
    private static final int ASSIGNEE_SAMPLE_LIMIT = 500;

    private static final AtomicLong cnt = new AtomicLong(0);
    private static List<Assignee> updateQueue = Collections.synchronizedList(new ArrayList<>(QUEUE_SIZE));

    private static String prepareInsertStatement(int size) {
        String qStr;
        String[] qs = new String[NUM_FIELDS];
        for(int j = 0; j < NUM_FIELDS; j++) {
            qs[j] = "?";
        }
        qStr = "("+String.join(",",qs)+")";

        String pref = "insert into assignees_raw (name,city,state,country,role,entity_status,human) values ";
        String suff = ";";
        StringJoiner s = new StringJoiner(", ",pref,suff);
        for(int i = 0; i < size; i++) {
            s.add(qStr);
        }
        return s.toString();
    }

    private static void addToQueue(String name, String city, String state, String country, String role, String entityStatus, boolean human) throws SQLException {
        Assignee assignee = new Assignee(name,city,state,country,role,entityStatus,human);
        synchronized (Seed.class) {
            updateQueue.add(assignee);
        }
    }

    private static void flush(Connection conn) throws SQLException {
        List<Assignee> updateQueueCopy;
        synchronized (Seed.class) {
            if (updateQueue.isEmpty()) return;
            updateQueueCopy = new ArrayList<>(updateQueue);
            updateQueue = Collections.synchronizedList(new ArrayList<>(QUEUE_SIZE));
        }

        PreparedStatement ps = conn.prepareStatement(prepareInsertStatement(updateQueueCopy.size()));
        for(int i = 0; i < updateQueueCopy.size(); i++) {
            int j = (i*NUM_FIELDS);
            Assignee a = updateQueueCopy.get(i);
            ps.setString(j+1, a.name);
            ps.setString(j+2, a.city);
            ps.setString(j+3, a.state);
            ps.setString(j+4, a.country);
            ps.setString(j+5, a.role);
            ps.setString(j+6, a.entityStatus);
            ps.setBoolean(j+7, a.human);
        }
        try {
            ps.executeUpdate();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed on query: "+ps.toString());
            System.exit(1);
        } finally {
            ps.close();
        }
        if(cnt.getAndIncrement()%COMMIT_N_BATCHES==COMMIT_N_BATCHES-1) {
            conn.commit();
        }
    }

    public static String toStringSafe(Object in) {
        return in==null||in.toString().length()==0?null:in.toString();
    }


    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);
        EntityTypeAttribute entityTypeAttribute = new EntityTypeAttribute();

        Map<String,AtomicInteger> assigneeCounts = Collections.synchronizedMap(new HashMap<>());

        Consumer<Document> consumer = doc -> {
            String filing = doc.getString(Constants.FILING_NAME);
            if(filing==null)return;

            String entityType = entityTypeAttribute.handleFiling(filing);

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
                        String nameStr = name.toString();
                        assigneeCounts.putIfAbsent(nameStr,new AtomicInteger(0));

                        if(assigneeCounts.get(nameStr).getAndIncrement()<ASSIGNEE_SAMPLE_LIMIT) {
                            Object city = assignee.get(Constants.CITY);
                            Object state = assignee.get(Constants.STATE);
                            Object country = assignee.get(Constants.COUNTRY);
                            Object role = assignee.get(Constants.ASSIGNEE_ROLE);

                            String cityStr = toStringSafe(city);
                            String stateStr = toStringSafe(state);
                            String countryStr = toStringSafe(country);
                            String roleStr = toStringSafe(role);

                            try {
                                addToQueue(nameStr, cityStr, stateStr, countryStr, roleStr, entityType, isHuman);
                                if(updateQueue.size()>=QUEUE_SIZE) flush(conn);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        };

        String[] fields = new String[]{
                Constants.ASSIGNEES,
                Constants.FILING_NAME
        };


        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,new Document(), DataIngester.TYPE_NAME,fields);

        flush(conn);
        conn.commit();
        conn.close();
    }

}

