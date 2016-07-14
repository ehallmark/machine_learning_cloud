package semantic_hash;

import learning.LabelSeeker;
import learning.MeansBuilder;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;


import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.MyPreprocessor;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ehallmark on 6/13/16.
 */
public class Training {

    private ParagraphVectors paragraphVectors;
    private DatabaseLabelAwareIterator iterator;
    private TokenizerFactory tokenizerFactory;
    private List<String> labels;

    public static void main(String[] args) throws Exception {

        Training app = new Training();
        app.makeParagraphVectors();
        app.saveParagraphVectors();
        app.checkUnlabeledData();
    }

    private void saveParagraphVectors() throws IOException {
        // dont overwrite
        File pVectors = new File(Constants.SEMANTIC_HASH_PARAGRAPH_VECTORS_FILE);
        if(pVectors.exists())pVectors.delete();
        // Write word vectors
        WordVectorSerializer.writeWordVectors(paragraphVectors, new File(Constants.SEMANTIC_HASH_PARAGRAPH_VECTORS_FILE));

    }

    void makeParagraphVectors()  throws Exception {
        // build a iterator for our dataset
        labels = new LinkedList<>();
        iterator = new DatabaseLabelAwareIterator(20110000,20150000);

        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());

        // ParagraphVectors training configuration
        paragraphVectors = new ParagraphVectors.Builder()
                .learningRate(0.001)
                .minLearningRate(0.0001)
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .epochs(1)
                .batchSize(1000)
                .windowSize(5)
                .iterations(3)
                .iterate(iterator)
                .layerSize(500)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .trainWordVectors(true)
                .trainElementsRepresentation(true)
                .trainSequencesRepresentation(false)
                .tokenizerFactory(tokenizerFactory)
                .build();

        // Start model training
        paragraphVectors.fit();


    }

    void checkUnlabeledData() throws Exception {
        // build a iterator for our dataset
        iterator = new DatabaseLabelAwareIterator(20150000,20160000);

        File pVectors = new File("SemanticHashParagraphVectors.txt");
        if(pVectors.exists()) {
            paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(pVectors);
        } else {
            throw new Exception("Cannot find paragraph vectors...");
        }
  /*
  At this point we assume that we have model built and we can check
  which categories our unlabeled document falls into.
  So we'll start loading our unlabeled documents and checking them
 */
        DatabaseLabelAwareIterator unClassifiedIterator = new DatabaseLabelAwareIterator(1000,0);

 /*
  Now we'll iterate over unlabeled data, and check which label it could be assigned to
  Please note: for many domains it's normal to have 1 document fall into few labels at once,
  with different "weight" for each.
 */
        MeansBuilder meansBuilder = new MeansBuilder(
                (InMemoryLookupTable<VocabWord>)paragraphVectors.getLookupTable(),
                tokenizerFactory);
        LabelSeeker seeker = new LabelSeeker(labels,
                (InMemoryLookupTable<VocabWord>) paragraphVectors.getLookupTable());

        while (unClassifiedIterator.hasNext()) {
            LabelledDocument document = new LabelledDocument();
            document.setContent(unClassifiedIterator.nextSentence());
            document.setLabel(unClassifiedIterator.currentLabel());
            System.out.println("Document label: "+document.getLabel());
            INDArray documentAsCentroid = meansBuilder.documentAsVector(document);
            List<Pair<String, Double>> scores = seeker.getScores(documentAsCentroid);
            scores.sort((o1,o2)->o2.getSecond().compareTo(o1.getSecond()));


            System.out.println("Document '" + document.getLabel() + "' falls into the following categories: ");
            for (Pair<String, Double> score: scores.subList(0, 10)) {
                System.out.println("        " + score.getFirst() + ": " + score.getSecond());
            }
        }





    }


}

