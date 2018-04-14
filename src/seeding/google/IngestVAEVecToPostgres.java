package seeding.google;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class IngestVAEVecToPostgres {
    public static final String DELIMITER = ";";

    public static void main(String[] args) throws Exception {
        final int batchSize = 1000;

        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select tree from big_query_cpc_tree");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();

        DeepCPCVAEPipelineManager manager = DeepCPCVAEPipelineManager.getOrLoadManager();
        DeepCPCVariationalAutoEncoderNN encoder = (DeepCPCVariationalAutoEncoderNN)manager.getModel();

        Set<String> cpcCombos = new HashSet<>();
        int cnt = 0;
        while(rs.next()) {
            String[] tree = (String[])rs.getArray(1).getArray();
            Arrays.sort(tree);
            List<String> cpcs = new ArrayList<>(Arrays.asList(tree));
            cpcs.removeIf(cpc->!manager.getCpcToIdxMap().containsKey(cpc));
            cpcCombos.addAll(Arrays.asList(tree));
            cpcCombos.add(String.join(DELIMITER,cpcs));
            if(cnt%10000==9999) {
                System.out.println("Found trees for: "+cnt);
            }
            cnt++;
        }
        rs.close();
        ps.close();

        System.out.println("Num distinct cpcs combos: "+cpcCombos);

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
