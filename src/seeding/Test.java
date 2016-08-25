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
    public Test(WeightLookupTable<VocabWord> lookupTable, boolean wordsOnly) {
        // Test
        if(!wordsOnly) {
            StringJoiner sj = new StringJoiner("\n");
            sj.add(similarityMessage("8142281","7455590",lookupTable))
                    .add(similarityMessage("9005028","7455590",lookupTable))
                    .add(similarityMessage("8142843","7455590",lookupTable));
            new Emailer(sj.toString());
        } else {
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Similarity Report: ")
                    .add(Test.similarityMessage("computer","network",lookupTable))
                    .add(Test.similarityMessage("wireless","network",lookupTable))
                    .add(Test.similarityMessage("substrate","network",lookupTable))
                    .add(Test.similarityMessage("substrate","nucleus",lookupTable))
                    .add(Test.similarityMessage("substrate","chemistry",lookupTable));
            new Emailer(sj.toString());
        }
    }
    public Test(WeightLookupTable<VocabWord> lookupTable) { this(lookupTable,false);}

    public static String similarityMessage(String word1, String word2, WeightLookupTable<VocabWord> lookupTable) {
        double similarity = Transforms.cosineSim(lookupTable.vector(word1), lookupTable.vector(word2));
        return "Similarity between "+word1+" and "+word2+": " + similarity;
    }
}
