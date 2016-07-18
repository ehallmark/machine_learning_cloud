package seeding;


import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import tools.WordVectorSerializer;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/15/16.
 */
public class LoadValuablePatentNumbersToFile {
    private static File valuableFile = new File(Constants.VALUABLE_PATENTS_LIST_FILE);
    private static File unvaluableFile = new File(Constants.UNVALUABLE_PATENTS_LIST_FILE);
    private static File csvFile = new File(Constants.VALUABLE_PATENTS_CSV);

    private int numValuablePatents=0;
    private int numUnValuablePatents=0;
    private int numEpochs = 5;
    private TokenPreProcess preProcess;

    private LoadValuablePatentNumbersToFile() throws Exception {
        this.preProcess = new MyPreprocessor();
        Database.setupSeedConn();
        if(!valuableFile.exists()) {
            System.out.println("Starting to load valuable patents...");
            downloadValuablePatentsToFile();
        } else {
            System.out.println("Skipping valuable patents...");
        }
        if(!unvaluableFile.exists()) {
            System.out.println("Starting to load unvaluable patents...");
            downloadUnValuablePatentsToFile();
        } else {
            System.out.println("Skipping unvaluable patents...");
        }
        downloadClassificationDataToCSV();
        System.out.println("Number of valuable patents: "+numValuablePatents);
        System.out.println("Number of unvaluable patents: "+numUnValuablePatents);

        Database.close();
    }

    private void downloadValuablePatentsToFile() throws IOException, SQLException {
        List<String> patents = new LinkedList<>();
        ResultSet rs = Database.getValuablePatents();

        while(rs.next()) {
            System.out.println(rs.getString(1));
            patents.add(rs.getString(1));
        }
        numValuablePatents=patents.size();

        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(valuableFile)));
        oos.writeObject(patents);
        oos.flush();
        oos.close();
    }

    private void downloadClassificationDataToCSV() throws IOException, SQLException {
        System.out.println("Loading google model...");
        WordVectors wordVectors = WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH), true, false);
        System.out.println("Loaded google model...");
        int layerSize = wordVectors.lookupTable().layerSize();
        BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile));
        ResultSet rs = Database.getClassificationsAndTitleFromList(getValuablePatents());
        System.out.println("Starting Valuable Patents...");
        while(rs.next()) {
            bw.write(generateVectorLineForCSV(rs, "0", layerSize, wordVectors));
            bw.flush();
        }
        System.out.println("Starting Unvaluable Patents...");
        ResultSet rs2 = Database.getClassificationsAndTitleFromList(getUnValuablePatents());
        while(rs2.next()) {
            bw.write(generateVectorLineForCSV(rs2, "1", layerSize, wordVectors));
            bw.flush();
        }

        bw.close();

    }

    private String generateVectorLineForCSV(ResultSet rs, String label, int layerSize, WordVectors wordVectors) throws SQLException {
        String[] invention_title = new String[]{rs.getString(1)};
        String[] classifications = (String[])(rs.getArray(2).getArray());
        String[] subClassifications = (String[])(rs.getArray(3).getArray());

        StringJoiner line = new StringJoiner(",","",System.getProperty("line.separator"));
        for(double val : generateAverageWordVector(invention_title,layerSize,wordVectors)) {
            line.add(Double.toString(val));
        }
        for(double val : generateAverageWordVector(classifications,layerSize,wordVectors)) {
            line.add(Double.toString(val));
        }
        for(double val : generateAverageWordVector(subClassifications,layerSize,wordVectors)) {
            line.add(Double.toString(val));
        }
        line.add(label);
        return line.toString();
    }

    private double[] generateAverageWordVector(String[] classifications, int layerSize, WordVectors wordVectors) {
        double[] values = new double[layerSize];
        Arrays.fill(values, 0d);

        int numClasses = 0;
        for(String klass : classifications) {
            double[] classValues = new double[layerSize]; Arrays.fill(classValues, 0d);
            int numWordsInClass = 0;
            for(String word : preProcess.preProcess(klass).split("\\s+")) {
                if(Constants.STOP_WORD_SET.contains(word)) continue;
                double[] curWordVals = wordVectors.getWordVector(word);
                if(curWordVals==null)continue;
                for(int i = 0; i < layerSize; i++) {
                    classValues[i]+=curWordVals[i];
                }
                numWordsInClass++;
            }
            for(int i = 0; i < layerSize; i++) {
                values[i]+=(classValues[i]/numWordsInClass);
            }
            numClasses++;
        }

        for(int i = 0; i < layerSize; i++) {
            values[i]=values[i]/numClasses;
        }

        return values;
    }

    private void downloadUnValuablePatentsToFile() throws IOException, SQLException {
        List<String> patents = new LinkedList<>();
        ResultSet rs = Database.getUnValuablePatents();

        while(rs.next()) {
            System.out.println(rs.getString(1));
            patents.add(rs.getString(1));
        }
        numUnValuablePatents=patents.size();

        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(unvaluableFile)));
        oos.writeObject(patents);
        oos.flush();
        oos.close();
    }

    public static List<String> getValuablePatents() throws IOException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(valuableFile)));
        try {
            return (List<String>)ois.readObject();
        } catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
    }

    public static List<String> getUnValuablePatents() throws IOException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(unvaluableFile)));
        try {
            return (List<String>)ois.readObject();
        } catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            new LoadValuablePatentNumbersToFile();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
