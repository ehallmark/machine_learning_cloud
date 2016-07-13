package semantic_hash;

import learning.LabelSeeker;
import learning.MeansBuilder;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelAwareDocumentIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Created by ehallmark on 6/13/16.
 */
public class Testing {
    private DefaultTokenizerFactory tokenizerFactory;
    private ParagraphVectors paragraphVectors;
    private DatabaseLabelAwareIterator iterator;


    public Testing() throws Exception {
        setupTest();
        checkUnlabeledData();
    }

    void setupTest() throws Exception {
        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());

        // build a iterator for our dataset
        iterator = new DatabaseLabelAwareIterator(20150000,20160000);

        File pVectors = new File("SemanticHashParagraphVectors.txt");
        if(pVectors.exists()) {
            paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(pVectors);
        } else {
            throw new Exception("Cannot find paragraph vectors...");
        }
    }

    void checkUnlabeledData() throws Exception {
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
        LabelSeeker seeker = new LabelSeeker(iterator.getLabels(),
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

    public static void main(String[] args) {
        try{new Testing();}catch(Exception e) {e.printStackTrace();}
    }
}
