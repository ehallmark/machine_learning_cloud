package svm;

import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import svm.libsvm.svm_model;
import svm.libsvm.svm_parameter;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.AbstractPortfolio;

import java.util.*;

/**
 * Created by Evan on 5/16/2017.
 */
public class GatherSVMClassifier extends ClassificationAttr {
    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        return null;
    }

    @Override
    public int numClassifications() {
        return 0;
    }

    @Override
    public Collection<String> getClassifications() {
        return null;
    }

    public static void main(String[] args) {
        // build gather svm
// svm parameters
        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.gamma = 0.5;
        param.nu = 0.5;
        param.C = 100;
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.SIGMOID;
        param.cache_size = 20000;
        param.eps = 0.001;


        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getGatherTechnologyTrainingDataMap();
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());

        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies);

        System.out.println("Training svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        System.out.println("Testing svm model...");
        Pair<double[][],double[][]> testing = SVMHelper.mapToSVMData(SplitModelData.getGatherTechnologyTestDataMap(),orderedTechnologies);
        double[] ypred = SVMHelper.svmPredict(testing.getFirst(), m);
        for (int i = 0; i < testing.getFirst().length; i++){
            System.out.println("(Actual:" + Arrays.toString(testing.getSecond()[i]) + " Prediction:" + ypred[i] + ")");
        }
    }
}
