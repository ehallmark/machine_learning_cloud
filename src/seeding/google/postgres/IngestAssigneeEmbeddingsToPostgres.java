package seeding.google.postgres;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IngestAssigneeEmbeddingsToPostgres {

    private static Float[] boxed(float[] in) {
        Float[] vector = new Float[in.length];
        for(int v = 0; v < vector.length; v++) {
            vector[v]=in[v];
        }
        return vector;
    }

    private static float[] toUnitVec(float[] in) {
        float norm = 0f;
        for(float x : in) {
            norm+=x*x;
        }
        for(int i = 0; i < in.length; i++) {
            in[i]= in[i]/norm;
        }
        return in;
    }

    public static void main(String[] args) throws Exception {
        final int batchSize = 1000;

        // cpcs
        Map<String,float[]> assigneeToVecMap = new HashMap<>();
        int cnt = 0;
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select assignee_harmonized[1] as name,enc from patents_global as g join big_query_embedding_by_fam as f on (g.family_id=f.family_id) where g.family_id!='-1' and assignee_harmonized[1] is not null");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        Connection conn = Database.getConn();
        while(rs.next()) {
            String assignee = rs.getString(1);
            Double[] encoding = (Double[])rs.getArray(2).getArray();
            float[] previous = assigneeToVecMap.get(assignee);
            if(previous==null) {
                previous = new float[encoding.length];
            }
            for(int i = 0; i < encoding.length; i++) {
                previous[i]+=encoding[i];
            }
            assigneeToVecMap.put(assignee,previous);
            if(cnt%100==99) {
                System.out.print("-");
            }
            if(cnt%10000==9999) {
                System.out.println();
                System.out.println("Found vectors for: "+cnt);
            }
            cnt++;
        }
        rs.close();
        ps.close();
        seedConn.close();

        System.out.println("Num distinct assignees: "+assigneeToVecMap.size());

        PreparedStatement insert = conn.prepareStatement("insert into big_query_embedding_assignee (name,enc) values (?,?) on conflict (name) do update set enc=excluded.enc");
        List<String> allAssignees = new ArrayList<>(assigneeToVecMap.keySet());
        for(int i = 0; i < allAssignees.size(); i++) {
            String assignee = allAssignees.get(i);
            float[] vector = assigneeToVecMap.get(assignee);
            vector = toUnitVec(vector);
            insert.setString(1,assignee);
            insert.setArray(2, conn.createArrayOf("float4", boxed(vector)));
            insert.executeUpdate();
            if(i%1000==999) {
                System.out.println("Completed batch. Ingested: " + i);
                Database.commit();
            }
        }
        
        Database.commit();
        insert.close();
        conn.close();
    }
}
