package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.ops.transforms.Transforms;
import tools.Emailer;

import java.util.StringJoiner;

/**
 * Created by ehallmark on 8/24/16.
 */
public class Test {
    public Test(WeightLookupTable<VocabWord> lookupTable) {
        // Test
        double similarity1 = Transforms.cosineSim(lookupTable.vector("8142281"),lookupTable.vector("7455590"));
        double similarity2 = Transforms.cosineSim(lookupTable.vector("9005028"),lookupTable.vector("7455590"));
        double similarity3 = Transforms.cosineSim(lookupTable.vector("7455590"),lookupTable.vector("8142843"));
        System.out.println("Similarity between 8142281 and 7455590: "+similarity1);
        System.out.println("Similarity between 9005028 and 7455590 (hopefully slightly lower): "+similarity2);
        System.out.println("Similarity between 8142843 and 7455590 (should definitely be way lower): "+similarity3);

        StringJoiner sj = new StringJoiner("\n");
        sj.add("Similarity between 8142281 and 7455590: "+similarity1)
                .add("Similarity between 9005028 and 7455590 (hopefully slightly lower): "+similarity2)
                .add("Similarity between 8142843 and 7455590 (should definitely be way lower): "+similarity3);
        new Emailer(sj.toString());
    }
}
