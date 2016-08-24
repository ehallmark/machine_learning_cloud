package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import tools.Emailer;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/23/16.
 */
public class UpdateDBWithParagraphVectors {
    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Database.setupInsertConn();

        System.out.println("Starting to read paragraph vectors...");
        long time1 = System.currentTimeMillis();
        ParagraphVectors vectors = WordVectorSerializer.readParagraphVectorsFromText(new File(Constants.WORD_VECTORS_PATH));
        long time2 = System.currentTimeMillis();
        System.out.println("Time to read paragraph vectors: "+new Double(time2-time1)/1000+ " seconds...");
        WeightLookupTable<VocabWord> lookupTable = vectors.lookupTable();


        // Test
        double similarity1 = Transforms.cosineSim(lookupTable.vector("8142281"),lookupTable.vector("7455590"));
        double similarity2 = Transforms.cosineSim(lookupTable.vector("9005028"),lookupTable.vector("7455590"));
        double similarity3 = Transforms.cosineSim(lookupTable.vector("7455590"),lookupTable.vector("8142843"));
        System.out.println("Similarity between 8142281 and 7455590: "+similarity1);
        System.out.println("Similarity between 9005028 and 7455590 (hopefully slightly lower): "+similarity2);
        System.out.println("Similarity between 8142843 and 7455590 (should definitely be way lower): "+similarity3);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("Time to read paragraph vectors: "+new Double(time2-time1)/1000+ " seconds...")
                .add("Similarity between 8142281 and 7455590: "+similarity1)
                .add("Similarity between 9005028 and 7455590 (hopefully slightly lower): "+similarity2)
                .add("Similarity between 8142843 and 7455590 (should definitely be way lower): "+similarity3);
        new Emailer(sj.toString());


        ResultSet rs = Database.selectRawPatents();
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            String patentNumber = rs.getString(1);
            INDArray vec = lookupTable.vector(patentNumber);
            if(vec!=null) {
                Float[] dbVec = VectorHelper.toObject(vec.data().asFloat());
                System.gc();
                Database.updateParagraphVectorFor(patentNumber, dbVec);
                System.out.println(cnt.getAndIncrement());
                if(cnt.get() % 1000 == 0) {
                    Database.insertCommit();
                }
            }
        }
        Database.insertCommit();
        Database.close();
    }
}
