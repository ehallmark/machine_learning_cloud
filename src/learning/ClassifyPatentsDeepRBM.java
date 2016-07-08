package learning;

import org.canova.api.records.reader.RecordReader;
import org.canova.api.records.reader.impl.CSVRecordReader;
import org.canova.api.split.FileSplit;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.dataset.DataSet;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
public class ClassifyPatentsDeepRBM {
	private int outputNum;
	private int inputNum;
	private int numEpochs;
	private DataSetIterator iter;
	private MultiLayerConfiguration conf;
	private MultiLayerNetwork model;

	public ClassifyPatentsDeepRBM() throws Exception {
		// Get number of possible classifications
		inputNum = CountFiles.getNumberOfInputs();
		outputNum = CountFiles.getNumberOfClassifications();
		numEpochs = 10;
		configure();
		train();
		save();
		new testing.TestClassificationModel(model);
	}
	
	private void train() {
		model = new MultiLayerNetwork(conf);
        model.init();
        model.setListeners(Collections.singletonList((IterationListener) new ScoreIterationListener(1000)));

        System.out.println("Train model....");

		for(int n = 0; n < numEpochs; n++) {
			model.fit(iter);
		}

	}

	private void save() throws IOException {
		ModelSerializer.writeModel(model, new File("patent_model.txt"), true);
	}
	
	private void configure() throws IOException, InterruptedException{
		// Get data
		System.out.println("Load data...");

		System.out.println("Number of inputs: "+inputNum);
		System.out.println("Number of outputs: "+outputNum);

		File resource = new File("patent_data.csv");		
		int batchSize = 100;
        //Load the training data:
        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(resource));

        iter = new RecordReaderDataSetIterator(rr,batchSize,inputNum,outputNum);
        
        System.out.println("Configure model....");

        conf = new NeuralNetConfiguration.Builder()
        		.seed(123)
            	.iterations(10)
				.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
				.learningRate(0.001)
				//.learningRateDecayPolicy(LearningRatePolicy.Score)
				.updater(Updater.RMSPROP)
				.momentum(0.7)
				//.miniBatch(false)
				.list()
				.layer(0, new RBM.Builder()
						.nIn(inputNum)
						.nOut(1000)
						.activation("sigmoid")
						.lossFunction(LossFunction.RMSE_XENT)
						.visibleUnit(RBM.VisibleUnit.LINEAR)
						.hiddenUnit(RBM.HiddenUnit.BINARY)
						.weightInit(WeightInit.XAVIER)
						.build())
				.layer(1, new RBM.Builder()
						.nIn(1000)
						.nOut(500)
						.visibleUnit(RBM.VisibleUnit.BINARY)
						.hiddenUnit(RBM.HiddenUnit.BINARY)
						.lossFunction(LossFunction.RMSE_XENT)
						.weightInit(WeightInit.XAVIER)
						.build())
				.layer(2, new RBM.Builder()
						.nIn(500)
						.nOut(500)
						.visibleUnit(RBM.VisibleUnit.BINARY)
						.hiddenUnit(RBM.HiddenUnit.BINARY)
						.lossFunction(LossFunction.RMSE_XENT)
						.weightInit(WeightInit.XAVIER)
						.build())
				.layer(3, new OutputLayer.Builder()
						.nIn(500)
						.nOut(outputNum)
						.activation("softmax")
						.lossFunction(LossFunction.NEGATIVELOGLIKELIHOOD)
						.weightInit(WeightInit.XAVIER)
						.build())
				.pretrain(true)
				.backprop(false)
				.build();

	}
	
	public static void main(String[] args) {
		try {
			new ClassifyPatentsDeepRBM();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
