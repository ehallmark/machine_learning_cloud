package testing;

import learning.CountFiles;
import org.canova.api.records.reader.RecordReader;
import org.canova.api.records.reader.impl.CSVRecordReader;
import org.canova.api.split.FileSplit;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;

import java.io.File;
import java.io.IOException;

/**
 * Created by ehallmark on 6/16/16.
 */
public class TestClassificationModel {
    private MultiLayerNetwork model;
    private int inputNum;
    private int outputNum;

    public TestClassificationModel(MultiLayerNetwork inModel) throws Exception {
        inputNum = CountFiles.getNumberOfInputs();
        outputNum = CountFiles.getNumberOfClassifications();
        if(inModel==null)loadModel();
        else model=inModel;
        testModel();
    }

    private void loadModel() throws IOException {
        model = ModelSerializer.restoreMultiLayerNetwork(new File("patent_model.txt"));
    }

    private void testModel() throws IOException, InterruptedException {
        File resource = new File("patent_data_testing.csv");
        int batchSize = 100;
        //Load the training data:
        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(resource));

        DataSetIterator iter = new RecordReaderDataSetIterator(rr,batchSize,inputNum,outputNum);

        System.out.println("Testing...");
        Evaluation eval = new Evaluation(outputNum);
        while(iter.hasNext()) {
            System.out.print('-');
            DataSet t = iter.next();
            INDArray features = t.getFeatureMatrix();
            INDArray labels = t.getLabels();
            INDArray predicted = model.output(features,false);

            eval.eval(labels, predicted);
        }

        System.out.println(eval.stats()); // DONE!
    }

    public static void main(String[] args) {
        try {
            new TestClassificationModel(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
