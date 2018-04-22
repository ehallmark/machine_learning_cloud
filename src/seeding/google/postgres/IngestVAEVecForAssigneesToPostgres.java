package seeding.google.postgres;

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

        DeepCPCVAEPipelineManager manager = DeepCPCVAEPipelineManager.getOrLoadManager();
        DeepCPCVariationalAutoEncoderNN encoder = (DeepCPCVariationalAutoEncoderNN)manager.getModel();
        Map<String,Integer> cpcToIndexMap = manager.getCpcToIdxMap();

        // cpcs
        Map<String,List<String>> cpcToTreeMap = new HashMap<>();
        int cnt = 0;
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select code,tree from big_query_cpc_definition");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        Connection conn = Database.getConn();
        while(rs.next()) {
            String code = rs.getString(1);
            String[] tree = (String[])rs.getArray(2).getArray();
            List<String> cpcs = Stream.of(tree)
                    .filter(cpc->cpcToIndexMap.containsKey(cpc))
                    .sorted()
                    .collect(Collectors.toList());
            if(!cpcs.isEmpty()) {
                cpcToTreeMap.put(code, cpcs);
            }
            if(cnt%10000==9999) {
                System.out.println("Found trees for: "+cnt);
            }
            cnt++;
        }
        rs.close();
        ps.close();
        seedConn.close();

        System.out.println("Num distinct cpcs: "+cpcToTreeMap.size());

        PreparedStatement insert = conn.prepareStatement("insert into big_query_cpc_embedding1 (code,cpc_vae) values (?,?) on conflict (code) do update set cpc_vae=?");
        List<String> cpcCodes = new ArrayList<>(cpcToTreeMap.keySet());
        for(int i = 0; i < cpcCodes.size(); i+=batchSize) {
            List<List<String>> cpcList = new ArrayList<>(batchSize);
            List<String> codes = new ArrayList<>(batchSize);
            for(int j = i; j < i+batchSize&&j<cpcCodes.size(); j++) {
                String code = cpcCodes.get(j);
                codes.add(code);
                cpcList.add(cpcToTreeMap.get(code));
            }
            INDArray encoding = encoder.encodeCPCsMultiple(cpcList);
            encoding.diviColumnVector(encoding.norm2(1));
            float[] data = encoding.data().asFloat();
            int vectorSize = data.length/cpcList.size();
            for(int j = 0; j < cpcList.size(); j++) {
                String code = codes.get(j);
                Float[] vector = new Float[vectorSize];
                for(int v = 0; v < vector.length; v++) {
                    vector[v]=data[j*vectorSize+v];
                }
                insert.setString(1,code);
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
