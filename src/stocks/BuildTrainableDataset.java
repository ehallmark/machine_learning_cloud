package stocks;

import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/17/17.
 */
public class BuildTrainableDataset {
    public static final File trainFolder = new File(Constants.DATA_FOLDER+"stock_data/train.jobj");
    public static final File valFolder = new File(Constants.DATA_FOLDER+"stock_data/dev.jobj");
    public static final File testFolder = new File(Constants.DATA_FOLDER+"stock_data/test.jobj");
    static {
        if(!trainFolder.getParentFile().exists())trainFolder.getParentFile().mkdirs();
    }

    private static INDArray createFeatures(List<Pair<LocalDate,Double>> data, int start, int end) {
        double[][] x = new double[(end-start)-1][];
        int xIdx = 0;
        for(int i = start; i < end-1; i++) {
            LocalDate date = data.get(i).getFirst();
            double xi = data.get(i).getSecond();
            double xi_2 = data.get(i+1).getSecond();
            double t = xi+xi_2;
            double diff;
            if(t==0) diff = 0d;
            else diff = (xi_2-xi)/t;
            double[] d = new double[13];
            d[0] = diff;
            // month dummy
            d[date.getMonthValue()] = 1d;

            x[xIdx]=d;
            xIdx++;
        }
        return Nd4j.create(x);
    }

    private static INDArray createLabels(List<Pair<LocalDate,Double>> data, int start, int end) {
        double[][] x = new double[(end-start)-1][];
        int xIdx = 0;
        for(int i = start; i < end-1; i++) {
            LocalDate date = data.get(i).getFirst();
            double xi = data.get(i).getSecond();
            double xi_2 = data.get(i+1).getSecond();
            double t = xi+xi_2;
            double diff;
            if(t==0) diff = 0d;
            else diff = (xi_2-xi)/t;
            double[] d = new double[1];
            d[0] = diff;

            x[xIdx]=d;
            xIdx++;
        }
        return Nd4j.create(x);
    }

    public static void main(String[] args) throws Exception {
        Map<String,List<Pair<LocalDate,Double>>> stockOverTimeMap = ScrapeCompanyTickers.getAssigneeToStockPriceOverTimeMap();

        // define constants
        final int windowSizeMonthsBefore = 12;
        final int windowSizeMonthsAfter = 4;
        final int totalWindowSize = windowSizeMonthsBefore+windowSizeMonthsAfter;

        List<DataSet> dataSets = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger cnt = new AtomicInteger(0);
        stockOverTimeMap.entrySet().parallelStream().forEach(e->{
            System.out.println(""+(cnt.getAndIncrement()+1));
            List<Pair<LocalDate,Double>> data = e.getValue();
            final int numEntries = data.size()/totalWindowSize;
            for(int q = 0; q < numEntries; q++) {
                int inputStart = q * totalWindowSize;
                int inputEnd = inputStart + windowSizeMonthsBefore;
                int outputEnd = inputEnd + windowSizeMonthsAfter;

                // compute outputs
                try {
                    INDArray inputs = createFeatures(data, inputStart, inputEnd);
                    INDArray outputs = createLabels(data, inputEnd, outputEnd);
                    dataSets.add(new DataSet(inputs,outputs));

                } catch(Exception e2) {
                    e2.printStackTrace();

                }

            }
        });

        System.out.println("Total number of datasets: "+dataSets.size());

        // split data
        Collections.shuffle(dataSets);
        float testRatio = 0.1f;
        int testIdx = Math.round(testRatio*dataSets.size());
        List<DataSet> testData = new ArrayList<>(dataSets.subList(0,testIdx));
        List<DataSet> devData = new ArrayList<>(dataSets.subList(testIdx,2*testIdx));
        List<DataSet> trainData = new ArrayList<>(dataSets.subList(2*testIdx,dataSets.size()));

        Database.trySaveObject(testData,testFolder);
        Database.trySaveObject(devData,valFolder);
        Database.trySaveObject(trainData,trainFolder);
    }
}
