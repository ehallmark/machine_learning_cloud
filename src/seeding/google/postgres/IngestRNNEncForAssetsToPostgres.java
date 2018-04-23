package seeding.google.postgres;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.rnn_encoding_model.RNNEncodingIterator;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.Layer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IngestRNNEncForAssetsToPostgres {

    public static void main(String[] args) throws Exception {
        final int maxNumWords = 128;
        RNNTextEncodingPipelineManager pipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(true);
        pipelineManager.runPipeline(false,false,false,false,-1,false);
        RNNTextEncodingModel model = (RNNTextEncodingModel) pipelineManager.getModel();
        Word2Vec word2Vec = pipelineManager.getWord2Vec();
        Layer layer = model.getNet().getLayer("l1");
        Connection conn = Database.getConn();
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id, abstract from big_query_patent_english_abstract");
        ps.setFetchSize(50);
        PreparedStatement insert = conn.prepareStatement("insert into big_query_embedding2 (family_id,rnn_enc) values (?,?) on conflict (family_id) do update set rnn_enc=?");
        ResultSet rs = ps.executeQuery();
        int cnt = 0;
        while(rs.next()) {
            String familyId = rs.getString(1);
            String text = rs.getString(2);
            String[] words = Util.textToWordFunction.apply(text);
            if(words!=null) {
                List<String> validWords = Stream.of(words)
                        .filter(w -> word2Vec.hasWord(w))
                        .limit(maxNumWords)
                        .collect(Collectors.toList());
                if(validWords.size()>5) {
                    INDArray vec = RNNEncodingIterator.featuresFor(word2Vec, validWords).transpose();
                    vec=vec.reshape(1,vec.shape()[0],vec.shape()[1]);
                    INDArray encoding = layer.activate(vec, Layer.TrainingMode.TEST);
                    encoding = encoding.get(NDArrayIndex.point(0),NDArrayIndex.all(),NDArrayIndex.point(encoding.shape()[2]-1));
                    float[] _data = encoding.data().asFloat();
                    Float[] data = new Float[_data.length];
                    for(int i = 0; i < _data.length; i++) {
                        data[i]=_data[i];
                    }
                    insert.setString(1, familyId);
                    insert.setArray(2, conn.createArrayOf("float4", data));
                    insert.setArray(3, conn.createArrayOf("float4", data));
                    insert.executeUpdate();
                }
            }
            if(cnt%10000==9999) {
                System.out.println("Completed batch. Ingested: " + cnt);
                Database.commit();
            }
            cnt++;
        }
        Database.commit();
        rs.close();
        ps.close();
        seedConn.close();
        insert.close();
        conn.close();
    }
}
