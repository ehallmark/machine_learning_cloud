package models.assignee.database;

import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MergeRawAssignees {
    private static final File file = new File(Constants.DATA_FOLDER+"all_assignee_data_map.jobj");
    private static Map<String,Map<String,Object>> MODEL;


    private static final String baseQuery = "select name, top[1] from (select name, array_agg(?) as top from assignees_raw where ? is not null group by name,? order by name,count(*) desc) as temp;";

    private static Map<String,Map<String,Object>> loadRawAssigneeData(Connection conn) {
        String[] fields = new String[]{"city","state","country","role","entity_status","human"};
        Map<String,String> postgresFieldToESFieldMap = Collections.synchronizedMap(new HashMap<>());
        postgresFieldToESFieldMap.put("city",Constants.CITY);
        postgresFieldToESFieldMap.put("state",Constants.STATE);
        postgresFieldToESFieldMap.put("country",Constants.COUNTRY);
        postgresFieldToESFieldMap.put("role",Constants.ASSIGNEE_ROLE);
        postgresFieldToESFieldMap.put("entity_status",Constants.ASSIGNEE_ENTITY_TYPE);
        postgresFieldToESFieldMap.put("human",Constants.IS_HUMAN);
        return Stream.of(fields).parallel().map(pgField->{
            try {
                String esField = postgresFieldToESFieldMap.get(pgField);
                Map<String, Map<String, Object>> map = Collections.synchronizedMap(new HashMap<>());
                String query = baseQuery.replace("?", pgField);
                PreparedStatement ps = conn.prepareStatement(query);
                System.out.println("Query: "+ps.toString());
                ps.setFetchSize(50);
                ResultSet rs = ps.executeQuery();
                boolean boolField = pgField.equals("human");
                AtomicLong cnt = new AtomicLong(0);
                while (rs.next()) {
                    Map<String,Object> assigneeMap = Collections.synchronizedMap(new HashMap<>());
                    Object obj = boolField ? rs.getBoolean(2) : rs.getString(2);
                    String name = rs.getString(1);
                    if(obj!=null) {
                        assigneeMap.put(esField,obj);
                    }
                    map.put(name,assigneeMap);
                    if(cnt.getAndIncrement()%10000==9999) {
                        System.out.println("Finished "+pgField.toUpperCase()+": "+cnt.get());
                    }
                }
                rs.close();
                ps.close();
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
                    return m2;
                }).get();

    }

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);

        Map<String,Map<String,Object>> assigneeData = (Map<String,Map<String,Object>>) Database.tryLoadObject(file);
        if(assigneeData==null) {
            assigneeData=loadRawAssigneeData(conn);
        }
        System.out.println("Assignee data size: "+assigneeData.size());

        Database.trySaveObject(assigneeData, file);

        conn.close();
    }

    public static synchronized Map<String,Map<String,Object>> get() {
        if(MODEL==null) {
            MODEL = (Map<String,Map<String,Object>>) Database.tryLoadObject(file);
        }
        return MODEL;
    }
}
