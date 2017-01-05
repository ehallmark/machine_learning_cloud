package dl4j_neural_nets.classifiers;

import dl4j_neural_nets.listeners.CustomClassificationModelListener;
import dl4j_neural_nets.tests.ModelEvaluator;
import dl4j_neural_nets.tools.MyTokenizerFactory;
import dl4j_neural_nets.iterators.datasets.ParagraphVectorDataSetIterator;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/1/16.
 */
public class ClassificationExample {

    public static void main(String[] args) throws Exception {
        Word2Vec wordVectors = WordVectorSerializer.loadFullModel(new File("wordvectorexample.txt").getPath());
        WordVectors wordVectors2 = WordVectorSerializer.loadFullModel(new File("wordvectorexample.txt").getPath());

        TokenizerFactory t = new MyTokenizerFactory();
        wordVectors.setTokenizerFactory(new MyTokenizerFactory());

        int batchSize = 10;     //Number of examples in each minibatch
        int vectorSize = 400;   //Size of the word vectors. 300 in the Google News model
        int nEpochs = 100;        //Number of epochs (full passes of training data) to train on

        WeightLookupTable<VocabWord> lookupTable = wordVectors.lookupTable();//WordVectorSerializer.readParagraphVectorsFromText(new File("wordvectorexample.txt")).getLookupTable();
        WeightLookupTable<VocabWord> lookupTable2 = wordVectors2.lookupTable();//WordVectorSerializer.readParagraphVectorsFromText(new File("wordvectorexample2.txt")).getLookupTable();

        // creating SentenceIterator wrapping our training corpus
        LabelAwareIterator sentenceIter = //new BasicLineIterator(inputFile.getAbsolutePath());
                new FileLabelAwareIterator.Builder().addSourceFolder(new File("paravec/labeled/")).build();

        Map<String,List<String>> trainData = new HashMap<>();
        Map<String,List<String>> testData = new HashMap<>();
        while(sentenceIter.hasNextDocument()) {
            LabelledDocument doc = sentenceIter.nextDocument();
            System.out.println("Label: "+doc.getLabel());
            trainData.put(doc.getLabel(),Arrays.stream(doc.getContent().split("\\s+")).collect(Collectors.toList()));
            testData.put(doc.getLabel(),Arrays.stream(doc.getContent().split("\\s+")).collect(Collectors.toList()));
        }
        List<String> labels = new ArrayList<>(trainData.keySet());

        //DataSetIterators for training and testing respectively
        DataSetIterator train = new ParagraphVectorDataSetIterator(trainData,labels,batchSize,true,lookupTable);
        DataSetIterator test = new ParagraphVectorDataSetIterator(testData,labels,batchSize,true,lookupTable);
        while(train.hasNext()) {
            System.out.println(train.next(1).getFeatureMatrix().toString());
        }
        train.reset();

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                .updater(Updater.NESTEROVS)
                //.regularization(true).l2(1e-5)
                //.gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).gradientNormalizationThreshold(2.0)
                .weightInit(WeightInit.XAVIER)
                //.dropOut(0.6)
                .seed(41)
                .momentum(0.9)
                .learningRate(0.0001)
                //.dropOut(0.5)
                .list()
                .layer(0, new DenseLayer.Builder().activation("sigmoid").nIn(vectorSize).nOut(200)
                        .build())
                .layer(1, new OutputLayer.Builder().activation("softmax")
                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(200).nOut(labels.size()).build())
                .pretrain(false).backprop(true).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        net.setListeners(new ScoreIterationListener(100), new CustomClassificationModelListener(net,test,false));

        System.out.println("Starting training");
        for( int i=0; i<nEpochs; i++ ){
            net.fit(train);
            train.reset();
            System.out.println("Epoch " + i + " complete. Starting evaluation:");

            // Evaluate model
            String stats = new ModelEvaluator().evaluateModel(test,net);
            System.out.println(stats);
        }


        System.out.println("----- Example complete -----");
    }
}
