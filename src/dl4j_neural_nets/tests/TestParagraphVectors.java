package dl4j_neural_nets.tests;

import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import tools.Emailer;

/**
 * Created by ehallmark on 12/27/16.
 */
public class TestParagraphVectors {
    public static void main(String[] args) throws Exception {
        ParagraphVectors sentencesModel = ParagraphVectorModel.loadAllSentencesModel();

        // Evaluate model
        String stats = new ModelEvaluator().evaluateWordVectorModel(sentencesModel.getLookupTable(), "Sentences Model");
        System.out.println(stats);
        new Emailer(stats);

        for(int i = 0; i < 14; i++) {
            ParagraphVectors claimModel = ParagraphVectorModel.loadModel(ParagraphVectorModel.claimsParagraphVectorFile.getAbsolutePath()+i);

            // Evaluate model
            stats = new ModelEvaluator().evaluateWordVectorModel(claimModel.getLookupTable(), "Claim Model [Epoch "+i+"]");
            System.out.println(stats);
            new Emailer(stats);
        }
    }
}
