package tools;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import seeding.Constants;

public class CountFiles {
	public static void main(String[] args) {
		File root = new File(Constants.COMPDB_TRAIN_FOLDER);
		File[] headers = root.listFiles();
		int numFiles = 0;
		List<String> patentNumbers = new ArrayList<String>();
		for(int i = 0; i < headers.length; i++) {
			numFiles+=headers[i].listFiles().length;
			patentNumbers.addAll(Arrays.asList(headers[i].list()));
		}
		System.out.println("Number of Files: "+numFiles);
		patentNumbers.forEach(pnum->{
			System.out.println(pnum);
		});
	}
	
	public static List<String> getIngestedPatents() {
		File root = new File(Constants.COMPDB_TRAIN_FOLDER);
		File[] headers = root.listFiles();
		List<String> patentNumbers = new ArrayList<String>();
		for(int i = 0; i < headers.length; i++) {
			patentNumbers.addAll(Arrays.asList(headers[i].list()));
		}
		return patentNumbers;
	}

	public static int getNumberOfTrainingPatents() {
		File root = new File(Constants.COMPDB_TRAIN_FOLDER);
		File[] headers = root.listFiles();
		int patentCount = 0;
		for(int i = 0; i < headers.length; i++) {
			if(headers[i].isDirectory())patentCount+=headers[i].listFiles().length;
		}
		System.out.println("Total number of training patents: "+patentCount);
		return patentCount;
	}

	public static int getNumberOfInputs() throws ClassNotFoundException, IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File("patent_data.csv")));
		int length = br.readLine().split(",").length-1;
		br.close();
		return length;
	}

	public static int getNumberOfClassifications() throws ClassNotFoundException, IOException {
		ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(Constants.COMPDB_TECHNOLOGIES_INTEGER_TO_STRING_MAP))));
		Map<Integer,String> labelMap = (Map<Integer,String>)ois.readObject();
		ois.close();
		System.out.println("Total number of classifications: "+labelMap.size());
		return labelMap.size();
	}
}
