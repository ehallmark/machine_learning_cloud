package stocks;

import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.value_models.regression.AIValueModel;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 11/17/17.
 */
public class BuildTrainableDataset {
    private static final File trainFolder = new File(Constants.DATA_FOLDER+"stock_data/train.jobj");
    private static final File valFolder = new File(Constants.DATA_FOLDER+"stock_data/dev.jobj");
    private static final File testFolder = new File(Constants.DATA_FOLDER+"stock_data/test.jobj");
    static {
        if(!trainFolder.getParentFile().exists())trainFolder.mkdirs();
        if(!valFolder.getParentFile().exists())valFolder.mkdirs();
        if(!testFolder.getParentFile().exists())testFolder.mkdirs();
    }

    public static void main(String[] args) throws Exception {
        File csvFile = BuildCSVDataset.csvOutputFile;

        // define constants
        final int nMonths = 3;
        final int kYears = 7;
        final int kMonths = kYears*12;
        final int yMonths = 20*12 - kMonths;

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        List<DataSet> dataSets = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger cnt = new AtomicInteger(0);
        br.lines().sequential().skip(1).parallel().forEach(line->{
            System.out.println(""+(cnt.getAndIncrement()+1)+" out of 4262");
            String[] cells = line.split(",");
            int numEntries = (cells.length-1)/3;
            for(int q = 0; q < numEntries; q++) {
                int inputStart = 1 + (3*q);
                int outputStart = inputStart + kMonths;
                if(outputStart >= cells.length-nMonths) continue;

                // compute inputs
                double numFilings = IntStream.range(0,nMonths).map(i->Integer.valueOf(cells[inputStart+1+(3*i)])).sum();

                // compute outputs
                AtomicInteger validCount = new AtomicInteger(0);
                double stockIncreaseTplusK = IntStream.range(1,yMonths).mapToDouble(i->{
                    int idx1 = outputStart + (3*(i-1));
                    int idx2 = idx1 + 3;
                    if(cells.length<=idx2) {
                        return 0d;
                    } else {
                        validCount.getAndIncrement();
                        double v2 = Double.valueOf(cells[idx2]);
                        double v1 = Double.valueOf(cells[idx1]);
                        if(v1<=0&&v2<=0) return 0d;
                        return (v2-v1)/((v1+v2)/2d);
                    }
                }).sum();

                INDArray features = Nd4j.create(new double[]{numFilings});
                INDArray labels = Nd4j.create(new double[]{stockIncreaseTplusK});
                dataSets.add(new DataSet(features,labels));
            }
        });

        System.out.println("Total number of datasets: "+dataSets.size());

        // split data
        Collections.shuffle(dataSets);
        float testRatio = 0.1f;
        int testIdx = Math.round(testRatio*dataSets.size());
        List<DataSet> testData = dataSets.subList(0,testIdx);
        List<DataSet> devData = dataSets.subList(testIdx,2*testIdx);
        List<DataSet> trainData = dataSets.subList(2*testIdx,dataSets.size());

        Database.trySaveObject(testData,testFolder);
        Database.trySaveObject(devData,valFolder);
        Database.trySaveObject(trainData,trainFolder);
    }
}
