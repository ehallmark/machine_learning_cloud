package seeding;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.deeplearning4j.text.documentiterator.LabelledDocument;

public class LoadDatabaseToFile {
	
	public LoadDatabaseToFile() throws Exception {
		// Training Data
		DatabaseIterator iter = new DatabaseIterator(false);
		String rootFolder = Constants.COMPDB_TRAIN_FOLDER;
		while(iter.hasNextDocuments()) {
			List<LabelledDocument> documents = iter.nextDocuments();
			Set<String> labels = new HashSet<>(iter.getCurrentLabels());
			String patentNumber = iter.getCurrentPatentNumber();
			int i = 0;
			for(LabelledDocument doc : documents) {
				for(String label : labels) {
					File folder = new File(rootFolder+label);
					if(!(folder.exists()&&folder.isDirectory())) {
						// make folder
						folder.mkdirs();
					}
					writeToFile(rootFolder+label+"/"+patentNumber+"_"+i, doc.getContent());
				}
				i++;
			}

		}
		
		// Testing Data
		DatabaseIterator testIter = new DatabaseIterator(true);
		String testFolder = Constants.COMPDB_TEST_FOLDER;
		while(testIter.hasNextDocuments()) {
			List<LabelledDocument> documents = testIter.nextDocuments();
			String patentNumber = testIter.getCurrentPatentNumber();
			int i = 0;
			for(LabelledDocument doc : documents) {
				writeToFile(testFolder+patentNumber+"_"+i, doc.getContent());
				i++;
			}

		}
	}
	
	public void writeToFile(String filename, String contents) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filename)));
		System.out.println(filename);
		bw.write(contents);
		bw.flush();
		bw.close();
	}
	
	public static void main(String[] args) {
		try {
			new LoadDatabaseToFile();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
