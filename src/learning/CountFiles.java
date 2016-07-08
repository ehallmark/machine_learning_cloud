package learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
			patentCount+=headers[i].listFiles().length;
		}
		return patentCount;
	}

	public static int getNumberOfInputs() throws ClassNotFoundException, java.io.IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File("patent_data.csv")));
		int length = br.readLine().split(",").length-1;
		br.close();
		return length;
	}

	public static int getNumberOfClassifications() {
		File root = new File(Constants.COMPDB_TRAIN_FOLDER);
		File[] headers = root.listFiles();
		if(headers == null) return 0;
		int num = 0;
		for(int i = 0; i < headers.length; i++) {
			if(headers[i].isDirectory()) num++;
		}
		return num;
	}
}
