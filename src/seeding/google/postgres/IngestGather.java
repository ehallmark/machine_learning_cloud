package seeding.google.postgres;

import seeding.Database;
import seeding.ai_db_updater.RestoreGatherAndCompDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IngestGather {
    public static void main(String[] args) throws Exception {
        boolean reloadGather = false;
        boolean reloadDatabase = false;
        if(reloadGather) {
            RestoreGatherAndCompDB.main(null);
        }
        if(reloadDatabase) {
            Database.main(null); // required to update gather attributes
        }

        Set<String> patents = new HashSet<>();

        Map<String,Integer> valueMap = Database.getGatherIntValueMap();
        Map<String,Collection<String>> stagesMap = Database.getGatherPatentToStagesCompleteMap();
        Map<String,Collection<String>> technologyMap = Database.getGatherPatentToTechnologyMap();

        patents.addAll(valueMap.keySet());
        patents.addAll(stagesMap.keySet());
        patents.addAll(technologyMap.keySet());


        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into big_query_gather (publication_number,value,stage,technology) values (?,?,?,?) on conflict (publication_number) do update set (value,stage,technology)=(?,?,?)");

        for(String patent : patents) {
            ps.setString(1,patent);
            boolean solid = false;
            if(valueMap.containsKey(patent)) {
                solid=true;
                ps.setInt(2, valueMap.get(patent));
                ps.setInt(5,valueMap.get(patent));
            } else {
                ps.setObject(2,null);
                ps.setObject(5,null);
            }
            if(stagesMap.containsKey(patent)) {
                solid=true;
                ps.setArray(3, conn.createArrayOf("varchar",stagesMap.get(patent).toArray(new String[stagesMap.get(patent).size()])));
                ps.setArray(6, conn.createArrayOf("varchar",stagesMap.get(patent).toArray(new String[stagesMap.get(patent).size()])));
            } else {
                ps.setObject(3,null);
                ps.setObject(6,null);
            }
            if(technologyMap.containsKey(patent)) {
                solid=true;
                ps.setArray(4, conn.createArrayOf("varchar",technologyMap.get(patent).toArray(new String[technologyMap.get(patent).size()])));
                ps.setArray(7, conn.createArrayOf("varchar",technologyMap.get(patent).toArray(new String[technologyMap.get(patent).size()])));
            } else {
                ps.setObject(4,null);
                ps.setObject(7,null);
            }
            if(solid) {
                ps.executeUpdate();
            }
        }


        Database.commit();
        Database.close();
    }
}
