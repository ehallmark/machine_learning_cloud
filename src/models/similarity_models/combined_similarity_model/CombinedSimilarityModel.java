package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.CombinedModel;
import data_pipeline.helpers.Function2;
import data_pipeline.models.CombinedNeuralNetworkPredictionModel;
import models.NDArrayHelper;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityModel extends CombinedNeuralNetworkPredictionModel<INDArray> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_model_data");
    public static final Function2<INDArray,INDArray,INDArray> DEFAULT_LABEL_FUNCTION = (f1,f2) -> Nd4j.hstack(f1,f2);

    private CombinedSimilarityPipelineManager pipelineManager;
    public CombinedSimilarityModel(CombinedSimilarityPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    public void train(int nEpochs) {
        if(net==null) {
            Map<String,MultiLayerNetwork> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());

            // build networks


            this.net = new CombinedModel(nameToNetworkMap);
        }


        MultiLayerNetwork wordCpc2Vec = net.getNameToNetworkMap().get("wordCpc2Vec");
        MultiLayerNetwork cpcVecNet = net.getNameToNetworkMap().get("cpcVecNet");

        DataSetIterator dataSetIterator = pipelineManager.getDatasetManager().getTrainingIterator();

        for(int i = 0; i < nEpochs; i++) {
            while(dataSetIterator.hasNext()) {
                DataSet ds = dataSetIterator.next();
                train(cpcVecNet,wordCpc2Vec,ds.getFeatures(),ds.getLabels());
            }
            dataSetIterator.reset();
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    public static void train(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        net1.fit(new DataSet(features1, labels));
        net2.fit(new DataSet(features2, labels));
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2, Function2<INDArray, INDArray, INDArray> featuresToLabelFunction) {
        INDArray labels = featuresToLabelFunction.apply(features1, features2);
        return new Pair<>(test(net1,features1,labels),test(net2,features2,labels));
    }

    public static double test(MultiLayerNetwork net, INDArray features, INDArray labels) {
        INDArray predictions = net.activateSelectedLayers(0,net.getnLayers()-1,features);
        return NDArrayHelper.sumOfCosineSimByRow(predictions,labels);
    }

}
