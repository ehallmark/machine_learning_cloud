package dl4j_neural_nets.tests;

import dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import dl4j_neural_nets.listeners.CustomWordVectorListener;
import dl4j_neural_nets.tools.MyTokenizerFactory;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Constants;
import tools.Emailer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by ehallmark on 12/27/16.
 */
public class TestParagraphVectors {
    public static void main(String[] args) throws Exception {
        int numEpochs = 3;
        File testFile = new File("testFile.pvectors");
        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentParagraphSamplingSequenceIterator(numEpochs,0.01);
        ParagraphVectors net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(1000)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(6)
                .layerSize(300)
                .sampling(0.00005)
                .negativeSample(-1)
                .learningRate(0.05)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(3)
                .workers(4)
                .iterations(1)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(new MyTokenizerFactory())
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(testFile,"Paragraph Vectors All Paragraphs",5000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();
        WordVectorSerializer.writeParagraphVectors(net, testFile.getAbsolutePath());


        ParagraphVectors sentencesModel = ParagraphVectorModel.loadModel(testFile.getAbsolutePath());

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
