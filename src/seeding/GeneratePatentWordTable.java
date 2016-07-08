package seeding;

import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.sql.SQLException;
import java.util.Arrays;

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
			DatabaseIterator iterator = new DatabaseIterator(false);
			int i = 0;
			while(iterator.hasNextDocument()) {
				for(String word : tokenizerFactory.create(iterator.nextDocument().getContent()).getTokens()) {
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
