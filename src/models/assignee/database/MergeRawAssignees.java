package models.assignee.database;

import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MergeRawAssignees {
    private static final int QUEUE_SIZE = 100;
    private static final int NUM_FIELDS = 7;
    private static final int COMMIT_N_BATCHES = 100;

    private static final AtomicLong cnt = new AtomicLong(0);
    private static final List<Pair<String,Map<String,Object>>> updateQueue = Collections.synchronizedList(new ArrayList<>(QUEUE_SIZE));

    private static final String baseQuery = "select name, top[1] from (select name, array_agg(?) as top from assignees_raw where ? is not null group by name,? order by name,count(*) desc) as temp;";

    private static Map<String,Map<String,Object>> loadRawAssigneeData(Connection conn) {
        String[] fields = new String[]{"normalized_name","city","state","country","role","human"};
        return Stream.of(fields).parallel().map(field->{
            try {
                Map<String, Map<String, Object>> map = Collections.synchronizedMap(new HashMap<>());
                String query = baseQuery.replace("?", field);
                PreparedStatement ps = conn.prepareStatement(query);
                ps.setFetchSize(50);
                ResultSet rs = ps.executeQuery();
                boolean boolField = field.equals("human");
                AtomicLong cnt = new AtomicLong(0);
                while (rs.next()) {
                    Map<String,Object> assigneeMap = Collections.synchronizedMap(new HashMap<>());
                    Object obj = boolField ? rs.getBoolean(2) : rs.getString(2);
                    String name = rs.getString(1);
                    if(obj!=null) {
                        assigneeMap.put(field,obj);
                    }
                    map.put(name,assigneeMap);
                    if(cnt.getAndIncrement()%10000==9999) {
                        System.out.println("Finished "+field.toUpperCase()+": "+cnt.get());
                    }
                }
                return map;
            } catch(Exception e) {
                return null;
            }
        }).filter(map->map!=null)
                .reduce((m1,m2)->{
                    m1.entrySet().parallelStream()
                            .forEach(e->{
                                m2.merge(e.getKey(),e.getValue(),(v1,v2)->{
                                    v1.putAll(v2);
                                    return v1;
                                });
                            });
                    return m1;
                }).get();

    }


    private static String prepareInsertStatement(int size) {
        String qStr;
        String[] qs = new String[NUM_FIELDS];
        for(int j = 0; j < NUM_FIELDS; j++) {
            qs[j] = "?";
        }
        qStr = "("+String.join(",",qs)+")";

        String pref = "insert into assignees (name,normalized_name,city,state,country,role,human) values ";
        String suff = ";";
        StringJoiner s = new StringJoiner(", ",pref,suff);
        for(int i = 0; i < size; i++) {
            s.add(qStr);
        }
        return s.toString();
    }

    private static void addToQueue(Connection conn, String name, Map<String,Object> fieldsMap) throws SQLException {
        synchronized (updateQueue) {
            if(updateQueue.size()>=QUEUE_SIZE) {
                flush(conn);
            }
        }
        synchronized (updateQueue) {
            updateQueue.add(new Pair<>(name,fieldsMap));
        }
    }

    private static void flush(Connection conn) throws SQLException {
        synchronized (updateQueue) {
            PreparedStatement ps = conn.prepareStatement(prepareInsertStatement(updateQueue.size()));
            for(int i = 0; i < updateQueue.size(); i++) {
                int j = (i*NUM_FIELDS);
                Pair<String,Map<String,Object>> a = updateQueue.get(i);
                String name = a.getFirst();
                Map<String,Object> dataMap = a.getSecond();
                String normalizedName = Seed.toStringSafe(dataMap.get("normalized_name"));
                String city = Seed.toStringSafe(dataMap.get("city"));
                String state = Seed.toStringSafe(dataMap.get("state"));
                String country = Seed.toStringSafe(dataMap.get("country"));
                String role = Seed.toStringSafe(dataMap.get("role"));
                String human = Seed.toStringSafe(dataMap.get("human"));
                if(human!=null&&human.length()>0)human=human.substring(0,1);
                ps.setString(j+1, name);
                ps.setString(j+2, normalizedName);
                ps.setString(j+3, city);
                ps.setString(j+4, state);
                ps.setString(j+5, country);
                ps.setString(j+6, role);
                ps.setString(j+7, human);
            }
            ps.executeUpdate();
            updateQueue.clear();
            if(cnt.getAndIncrement()%COMMIT_N_BATCHES==COMMIT_N_BATCHES-1) {
                conn.commit();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);

        Map<String,Map<String,Object>> assigneeData = loadRawAssigneeData(conn);

        // upsert
        assigneeData.entrySet().parallelStream().forEach(e->{
            try {
                addToQueue(conn, e.getKey(), e.getValue());
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        conn.commit();
        conn.close();
    }
}
