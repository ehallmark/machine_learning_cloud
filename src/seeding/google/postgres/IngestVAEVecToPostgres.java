package seeding.google.postgres;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IngestVAEVecToPostgres {
    public static final String DELIMITER = ";";

    public static void main(String[] args) throws Exception {
        final int batchSize = 1000;

        DeepCPCVAEPipelineManager manager = DeepCPCVAEPipelineManager.getOrLoadManager();
        DeepCPCVariationalAutoEncoderNN encoder = (DeepCPCVariationalAutoEncoderNN)manager.getModel();
        Map<String,Integer> cpcToIndexMap = manager.getCpcToIdxMap();

        Set<String> cpcCombos = new HashSet<>();
        int cnt = 0;
        Map<String,String> pubToCpcStrMap = new HashMap<>();
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select publication_number_full,tree from big_query_cpc_tree");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        Connection conn = Database.getConn();
        PreparedStatement insertStr = conn.prepareStatement("insert into big_query_embedding1_help_by_pub (publication_number_full,cpc_str) values (?,?) on conflict (publication_number_full) do update set cpc_str=?");
        while(rs.next()) {
            String publication = rs.getString(1);
            String[] tree = (String[])rs.getArray(2).getArray();
            List<String> cpcs = Stream.of(tree)
                    .filter(cpc->cpcToIndexMap.containsKey(cpc))
                    .sorted()
                    .collect(Collectors.toList());
            cpcCombos.addAll(Arrays.asList(tree));
            String cpcStr = String.join(DELIMITER,cpcs);
            cpcCombos.add(cpcStr);
            pubToCpcStrMap.put(publication,cpcStr);
            insertStr.setString(1, publication);
            insertStr.setString(2, cpcStr);
            insertStr.setString(3,cpcStr);
            insertStr.executeUpdate();
            
            if(cnt%10000==9999) {
                System.out.println("Found trees for: "+cnt);
                Database.commit();
            }
            cnt++;
        }
        rs.close();
        ps.close();
        seedConn.close();
        Database.commit();

        System.out.println("Num distinct cpcs combos: "+cpcCombos.size());

        PreparedStatement insert = conn.prepareStatement("insert into big_query_embedding1_help (cpc_str,cpc_vae) values (?,?) on conflict (cpc_str) do update set cpc_vae=?");
        List<String> cpcComboList = new ArrayList<>(cpcCombos);
        for(int i = 0; i < cpcComboList.size(); i+=batchSize) {
            List<List<String>> cpcList = new ArrayList<>(batchSize);
            for(int j = i; j < i+batchSize&&j<cpcComboList.size(); j++) {
                cpcList.add(Arrays.asList(cpcComboList.get(j).split(DELIMITER)));
            }
            INDArray encoding = encoder.encodeCPCsMultiple(cpcList);
            encoding.diviColumnVector(encoding.norm2(1));
            float[] data = encoding.data().asFloat();
            int vectorSize = data.length/cpcList.size();
            for(int j = 0; j < cpcList.size(); j++) {
                Float[] vector = new Float[vectorSize];
                for(int v = 0; v < vector.length; v++) {
                    vector[v]=data[j*vectorSize+v];
                }
                insert.setString(1,String.join(DELIMITER,cpcList.get(j)));
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
