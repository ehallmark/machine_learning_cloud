package seeding.google.postgres;

import cpc_normalization.CPCHierarchy;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IngestVAEVecForAssigneesToPostgres {
    public static void main(String[] args) throws Exception {
        final int batchSize = 1000;
        final int maxNumCpcsPerAssignee = 30;

        DeepCPCVAEPipelineManager manager = DeepCPCVAEPipelineManager.getOrLoadManager();
        DeepCPCVariationalAutoEncoderNN encoder = (DeepCPCVariationalAutoEncoderNN)manager.getModel();
        Map<String,Integer> cpcToIndexMap = manager.getCpcToIdxMap();

        // cpcs
        Map<String,List<String>> assigneeToTreeMap = new HashMap<>();
        int cnt = 0;
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select name,code from big_query_assignee_embedding1_help");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        Connection conn = Database.getConn();
        CPCHierarchy hierarchy = CPCHierarchy.get();
        while(rs.next()) {
            String assignee = rs.getString(1);
            String[] allAssigneeCpcs = (String[])rs.getArray(2).getArray();
            // group by counts and take top cpcs
            List<String> topCpcs = Stream.of(allAssigneeCpcs)
                    .collect(Collectors.groupingBy(e->e,Collectors.counting()))
                    .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                    .limit(maxNumCpcsPerAssignee)
                    .map(e->e.getKey())
                    .collect(Collectors.toList());

            List<String> cpcs = topCpcs.stream()
                    .flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream().map(c->c.getName()))
                    .distinct()
                    .filter(cpc->cpcToIndexMap.containsKey(cpc))
                    .sorted()
                    .collect(Collectors.toList());
            if(!cpcs.isEmpty()) {
                assigneeToTreeMap.put(assignee, cpcs);
            }
            if(cnt%10000==9999) {
                System.out.println("Found trees for: "+cnt);
            }
            cnt++;
        }
        rs.close();
        ps.close();
        seedConn.close();

        System.out.println("Num distinct assignees: "+assigneeToTreeMap.size());

        PreparedStatement insert = conn.prepareStatement("insert into big_query_assignee_embedding1 (name,cpc_vae) values (?,?) on conflict (name) do update set cpc_vae=?");
        List<String> allAssignees = new ArrayList<>(assigneeToTreeMap.keySet());
        for(int i = 0; i < allAssignees.size(); i+=batchSize) {
            List<List<String>> cpcList = new ArrayList<>(batchSize);
            List<String> assignees = new ArrayList<>(batchSize);
            for(int j = i; j < i+batchSize&&j<allAssignees.size(); j++) {
                String assignee = allAssignees.get(j);
                assignees.add(assignee);
                cpcList.add(assigneeToTreeMap.get(assignee));
            }
            INDArray encoding = encoder.encodeCPCsMultiple(cpcList);
            encoding.diviColumnVector(encoding.norm2(1));
            float[] data = encoding.data().asFloat();
            int vectorSize = data.length/cpcList.size();
            for(int j = 0; j < cpcList.size(); j++) {
                String assignee = assignees.get(j);
                Float[] vector = new Float[vectorSize];
                for(int v = 0; v < vector.length; v++) {
                    vector[v]=data[j*vectorSize+v];
                }
                insert.setString(1,assignee);
                insert.setArray(2, conn.createArrayOf("float4", vector));
                insert.setArray(3, conn.createArrayOf("float4", vector));
                insert.executeUpdate();
            }
            System.out.println("Completed batch. Ingested: "+i);
            Database.commit();
        }

        insert.close();
        conn.close();
    }
}
