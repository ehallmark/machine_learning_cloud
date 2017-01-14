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
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.parallelism.AsyncIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.GetEtsiPatentsList;
import tools.Emailer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/27/16.
 */
public class TestParagraphVectors {
    public static void main(String[] args) throws Exception {
        int numEpochs = 1;
        File testFile = new File("testFile.pvectors");
        List<String> patents = new ArrayList<>();
        GetEtsiPatentsList.getETSIPatentMap().values().forEach(list->{
            patents.addAll(list.stream().limit(2).collect(Collectors.toList()));
        });

        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentParagraphSamplingSequenceIterator(numEpochs, patents);

        ParagraphVectors net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(1000)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(6)
                .layerSize(300)
                .sampling(0.00005)
                .negativeSample(30)
                .learningRate(0.05)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(3)
                .workers(1)
                .iterations(1)
                //.stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                //.useHierarchicSoftmax(true)
                .tokenizerFactory(new MyTokenizerFactory())
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(null, "Paragraph Vectors Test", 5000, null, "7455590", "claim", "alkali_metal", "device", "femto", "finance", "touchscreen", "smartphone", "internet", "semiconductor", "artificial", "intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();

        WordVectorSerializer.writeParagraphVectors(net, testFile.getAbsolutePath());


        ParagraphVectors vectors = ParagraphVectorModel.loadModel(testFile.getAbsolutePath());


        INDArray inferredVectorA = vectors.inferVector("This is my world .");
        INDArray inferredVectorA2 = vectors.inferVector("This is my world .");
        INDArray inferredVectorB = vectors.inferVector("This is my way .");

        // high similarity expected here, since in underlying corpus words WAY and WORLD have really close context
        System.out.println("Cosine similarity A/B: " + Transforms.cosineSim(inferredVectorA, inferredVectorB));

        // equality expected here, since inference is happening for the same sentences
        System.out.println("Cosine similarity A/A2: " + Transforms.cosineSim(inferredVectorA, inferredVectorA2));
        patents.forEach(patent->{
            INDArray vec = vectors.getLookupTable().vector(patent);
            System.out.println("Vector for "+patent+": "+vec);
            if(vec!=null) {
                Collection<String> similar = vectors.nearestLabels(vec, 10);
                System.out.println("Similar lables to " + patent + ": " + String.join(", ", similar));
            }
        });

        System.out.print("Labels: ");
        vectors.getVocab().tokens().forEach(token->{
            if(token.isLabel()) {
                System.out.print(token.getLabel()+" ");
            }
        });

    }

}
