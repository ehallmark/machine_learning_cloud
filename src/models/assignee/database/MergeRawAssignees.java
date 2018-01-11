package models.assignee.database;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MergeRawAssignees {

    private static final String baseQuery = "select name, top[1] from (select name, array_agg(?) as top from assignees_raw where ? is not null group by name,? order by name,count(*) desc) as temp;";

    private static Map<String,Map<String,Object>> loadRawAssigneeData(Connection conn) throws SQLException {
        String[] fields = new String[]{"normalized_name","city","state","country","role","human"};
        return Stream.of(fields).parallel().map(field->{
            try {
                Map<String, Map<String, Object>> map = Collections.synchronizedMap(new HashMap<>());
                String query = baseQuery.replace("?", field);
                PreparedStatement ps = conn.prepareStatement(query);
                ps.setFetchSize(50);
                ResultSet rs = ps.executeQuery();
                boolean boolField = field.equals("human");
                while (rs.next()) {
                    Map<String,Object> assigneeMap = Collections.synchronizedMap(new HashMap<>());
                    Object obj = boolField ? rs.getBoolean(2) : rs.getString(2);
                    String name = rs.getString(1);
                    if(obj!=null) {
                        assigneeMap.put(field,obj);
                    }
                    map.put(name,assigneeMap);
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

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getOrSetupAssigneeConn();
        conn.setAutoCommit(false);
    }
}
