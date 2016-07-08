package testing;

import learning.LabelSeeker;
import learning.MeansBuilder;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.FilenamesLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import seeding.DatabaseIterator;
import seeding.MyPreprocessor;

import java.io.*;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class LenientTest {
	private DefaultTokenizerFactory tokenizerFactory;
    private ParagraphVectors paragraphVectors;
	
	public LenientTest()  throws Exception {
		setupTest();
		checkUnlabeledData();
	}
	
	void setupTest() throws Exception {
		// Check for paragraphVectors.obj file
		tokenizerFactory = new DefaultTokenizerFactory();
		tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());

		File pVectors = new File("paragraphVectors.txt");
		if(pVectors.exists()) {
			paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(pVectors);
		} else {
			throw new Exception("Cannot find paragraph vectors...");
		}
		System.out.println("Finished setting up test...");
	}
	
	@SuppressWarnings("unchecked")
	void checkUnlabeledData() throws Exception {
		/*
			At this point we assume that we have model built and we can check 
			which categories our unlabeled document falls into.
			So we'll start loading our unlabeled documents and checking them
	    */
		DatabaseIterator unClassifiedIterator = new DatabaseIterator(true);
	    /*
			Now we'll iterate over unlabeled data, and check which label it could be assigned to
			Please note: for many domains it's normal to have 1 document fall into few labels at once,
			with different "weight" for each.
		*/
		MeansBuilder meansBuilder = new MeansBuilder(
				(InMemoryLookupTable<VocabWord>)paragraphVectors.getLookupTable(),
				tokenizerFactory);
		LabelSeeker seeker = new LabelSeeker((new DatabaseIterator(false)).getLabels(),
				(InMemoryLookupTable<VocabWord>) paragraphVectors.getLookupTable());
		
		// Test statistics
		int numTotal = 0;
		int numCorrect = 0;

		
		while (unClassifiedIterator.hasNextDocuments()) {
			List<LabelledDocument> documents = unClassifiedIterator.nextDocuments();
			if(documents.isEmpty())continue;

			String patent = documents.get(0).getLabel();
			System.out.println("Document label: "+patent);
			
			List<Pair<String, Double>> scores = null;
			try {
				INDArray documentAsCentroid = meansBuilder.documentAsVector(documents.get(0));
				for(LabelledDocument doc : documents.subList(1,documents.size())) {
					documentAsCentroid.add(meansBuilder.documentAsVector(doc));
				}
				documentAsCentroid.div(documents.size());
				 scores = seeker.getScores(documentAsCentroid);
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			scores.sort((o1,o2)->(o2.getSecond().compareTo(o1.getSecond())));
			
			/*
				please note, document.getLabel() is used just to show which document we're looking at now,
				as a substitute for printing out the whole document name.
				So, labels on these two documents are used like titles,
				just to visualize our classification done properly
			*/

			// update stats
			int numGuesses = 10;
			List<String> guesses = new ArrayList<>();
			for(int i =0; i < numGuesses; i++) {
				guesses.add(scores.get(i).getFirst());
			}
			List<String> technologies = new ArrayList<>(unClassifiedIterator.getCurrentLabels());
			if(technologies==null||technologies.isEmpty()) continue;
			String techString = String.join(",", technologies);
			
			System.out.println("Actual Tech: "+techString);
			System.out.println("Best Guesses: "+String.join(",", guesses));

			technologies.retainAll(guesses);
 			if(!technologies.isEmpty()) {
				System.out.println("Correct!");
				numCorrect++;
			} else {
				System.out.println("WRONG :(");
			}
			numTotal++;

		}  
		
		int percentageCorrect = (int)Math.round((double)(numCorrect*100))/(numTotal);
		
		System.out.println("TOTAL TEST CASES: "+numTotal);
		System.out.println("TOTAL TEST CASES ANSWERED CORRECTLY: "+numCorrect);
		System.out.println("PERCENTAGE TEST CASES ANSWERED CORRECT: %"+percentageCorrect);
		
	}
	
	
	public static void main(String[] args) {
		try {
			new LenientTest();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
