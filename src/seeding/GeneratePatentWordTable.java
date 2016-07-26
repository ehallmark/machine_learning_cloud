package seeding;

import java.io.File;
import java.sql.SQLException;

import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

public class GeneratePatentWordTable {
	private static DefaultTokenizerFactory tokenizerFactory;
	// Check for paragraphVectors.obj file
	static {
		tokenizerFactory = new DefaultTokenizerFactory();
		tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
	}

	public static void main(String[] args) {
		try {
			Database.setupSeedConn();
			Database.setupMainConn();
			SentenceIterator iterator = new BasePatentIterator(Constants.START_DATE);
			int i = 0;
			while(iterator.hasNext()) {
				for(String word : tokenizerFactory.create(iterator.nextSentence()).getTokens()) {
					try {
						if(!Constants.STOP_WORD_SET.contains(word))Database.addOrUpdateWord(word);
						System.out.print(word + ' ');
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					i = (i+1) % 1000;
					if(i == 0) {
						Database.commit();
					}
				}

				System.out.println();
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
