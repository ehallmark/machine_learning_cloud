package seeding.google.postgres;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.rnn_encoding_model.RNNEncodingIterator;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.layers.feedforward.dense.DenseLayer;
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
        final int batchSize = 2000;
        final int maxNumWords = 128;
        RNNTextEncodingPipelineManager pipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(true);
        pipelineManager.runPipeline(false,false,false,false,-1,false);
        RNNTextEncodingModel model = (RNNTextEncodingModel) pipelineManager.getModel();
        Word2Vec word2Vec = pipelineManager.getWord2Vec();
        Layer layer = model.getNet().getLayer("l1");
        Connection conn = Database.getConn();
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id, abstract from big_query_patent_english_abstract");
        ps.setFetchSize(10);
        PreparedStatement insert = conn.prepareStatement("insert into big_query_embedding2 (family_id,rnn_enc) values (?,?) on conflict (family_id) do update set rnn_enc=?");
        ResultSet rs = ps.executeQuery();
        int cnt = 0;
        final int vectorSize = 256;
        while(true) {
            int i = 0;
            INDArray features = Nd4j.create(batchSize,vectorSize,maxNumWords);
            List<String> familyIds = new ArrayList<>(batchSize);
            for(; i < batchSize && rs.next(); i++) {
                String familyId = rs.getString(1);
                String text = rs.getString(2);
                String[] words = Util.textToWordFunction.apply(text);
                if (words != null) {
                    List<String> validWords = Stream.of(words)
                            .filter(w -> word2Vec.hasWord(w))
                            .limit(maxNumWords)
                            .collect(Collectors.toList());
                    if (validWords.size() > 5) {
                        List<String> validWordsToLength = new ArrayList<>(maxNumWords);
                        while(validWordsToLength.size()<maxNumWords) {
                            for(String w : validWords) {
                                if(validWordsToLength.size()<maxNumWords) {
                                    validWordsToLength.add(w);
                                }
                            }
                        }
                        INDArray vec = RNNEncodingIterator.featuresFor(word2Vec, validWordsToLength).transpose();
                        features.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(0,vec.columns())).assign(vec);
                        familyIds.add(familyId);
                    } else {
                        i--;
                        continue;
                    }
                } else {
                    i--;
                    continue;
                }
            }

            if(i==0) break;
            if(i<batchSize) {
                features = features.get(NDArrayIndex.interval(0,i),NDArrayIndex.all(),NDArrayIndex.all());
            }
            INDArray encoding = layer.activate(features, Layer.TrainingMode.TEST);
            encoding = encoding.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(encoding.shape()[2]-1));
            encoding.diviColumnVector(encoding.norm2(1));
            for(int j = 0; j < i; j++) {
                String familyId = familyIds.get(j);
                INDArray vec = encoding.getRow(j);
                float[] _data = vec.data().asFloat();
                Float[] data = new Float[_data.length];
                for(int v = 0; v < _data.length; v++) {
                    data[v]=_data[v];
                }
                insert.setString(1, familyId);
                insert.setArray(2, conn.createArrayOf("float4", data));
                insert.setArray(3, conn.createArrayOf("float4", data));
                insert.executeUpdate();
                if(cnt%10000==9999) {
                    System.out.println("Completed batch. Ingested: " + cnt);
                    Database.commit();
                }
                cnt++;
            }
        }
        Database.commit();
        rs.close();
        ps.close();
        seedConn.close();
        insert.close();
        conn.close();
    }
}
