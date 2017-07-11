package models.svm;

import models.graphical_models.classification.CPCKMeans;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.classification_models.NaiveGatherClassifier;
import org.deeplearning4j.berkeley.Pair;
import models.svm.libsvm.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/4/17.
 */
public class SVMHelper {

    // helper for gather data
    public static Pair<double[][],double[][]> mapToSVMData(Map<String,Collection<String>> gatherMap, List<String> technologies, Map<String,INDArray> lookupTable) {
        Map<String,Collection<String>> invertedGatherMap = NaiveGatherClassifier.invert(gatherMap).entrySet().stream()
                .filter(e-> e.getValue().size()>0&&lookupTable.get(e.getKey())!=null)
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
        int N = invertedGatherMap.entrySet().stream().collect(Collectors.summingInt(e->e.getValue().size()));
        double[][] x = new double[N][];
        double[][] y = new double[N][];

        AtomicInteger idx = new AtomicInteger(0);
        invertedGatherMap.entrySet().parallelStream().forEach(e->{
            String patent = e.getKey();
            Collection<String> techs = e.getValue();
            techs.forEach(tech->{
                int i = idx.getAndIncrement();
                x[i] = lookupTable.get(patent).data().asDouble();
                y[i] = new double[]{technologies.indexOf(tech)};
            });
        });

        return new Pair<>(x,y);
    }

    public static Pair<double[][],double[][]> mapToCPCSVMData(Map<String,Collection<String>> gatherMap, List<String> technologies, List<String> classifications, int cpcDepth) {
        Map<String,Collection<String>> invertedGatherMap = NaiveGatherClassifier.invert(gatherMap).entrySet().stream().filter(e-> e.getValue().size()>0&& Database.classificationsFor(e.getKey()).size()>0).collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
        int N = invertedGatherMap.entrySet().stream().collect(Collectors.summingInt(e->e.getValue().size()));
        double[][] x = new double[N][];
        double[][] y = new double[N][];

        AtomicInteger idx = new AtomicInteger(0);
        invertedGatherMap.entrySet().parallelStream().forEach(e->{
            String patent = e.getKey();
            Collection<String> techs = e.getValue();
            techs.forEach(tech->{
                int i = idx.getAndIncrement();
                x[i] = CPCKMeans.classVectorForPatents(Arrays.asList(patent),classifications, cpcDepth);
                y[i] = new double[]{technologies.indexOf(tech)};
            });
        });

        return new Pair<>(x,y);
    }


    public static svm_model svmTrain(double[][] xtrain, double[][] ytrain, svm_parameter param) {
        svm_problem prob = new svm_problem();
        int recordCount = xtrain.length;
        int featureCount = xtrain[0].length;
        prob.y = new double[recordCount];
        prob.l = recordCount;
        prob.x = new svm_node[recordCount][featureCount];

        for (int i = 0; i < recordCount; i++){
            double[] features = xtrain[i];
            prob.x[i] = new svm_node[features.length];
            for (int j = 0; j < features.length; j++){
                svm_node node = new svm_node();
                node.index = j;
                node.value = features[j];
                prob.x[i][j] = node;
            }
            prob.y[i] = ytrain[i][0];
        }

        svm_model model = svm.svm_train(prob, param);

        return model;
    }

    public static double[][] svmPredictionDistribution(double[][]xtest, svm_model model) {
        double[][] estimates = new double[xtest.length][];

        for(int k = 0; k < xtest.length; k++) {

            double[] fVector = xtest[k];

            svm_node[] nodes = new svm_node[fVector.length];
            for (int i = 0; i < fVector.length; i++) {
                svm_node node = new svm_node();
                node.index = i;
                node.value = fVector[i];
                nodes[i] = node;
            }

            double[] prob_estimates = new double[model.nr_class];
            svm.svm_predict_probability(model, nodes, prob_estimates);
            estimates[k]=prob_estimates;
        }

        return estimates;
    }

    public static double[] svmPredict(double[][] xtest, svm_model model)
    {

        double[] yPred = new double[xtest.length];

        for(int k = 0; k < xtest.length; k++){

            double[] fVector = xtest[k];

            svm_node[] nodes = new svm_node[fVector.length];
            for (int i = 0; i < fVector.length; i++)
            {
                svm_node node = new svm_node();
                node.index = i;
                node.value = fVector[i];
                nodes[i] = node;
            }

            double[] prob_estimates = new double[model.nr_class];
            yPred[k] = svm.svm_predict_probability(model, nodes, prob_estimates);
            //System.out.println("Estimates: "+Arrays.toString(prob_estimates)+", Prediction:" + yPred[k] + ")");
        }

        return yPred;
    }



    // TEST
    public static void main(String [] args) {
        // models.svm parameters
        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.gamma = 0.5;
        param.nu = 0.5;
        param.C = 100;
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.LINEAR;
        param.cache_size = 20000;
        param.eps = 0.001;


        final int trainingSize = 1000;
        final int testSize = 10;

        double[][] xtrain = new double[trainingSize][];
        double[][] ytrain = new double[trainingSize][];

        double[][] xtest = new double[testSize][];
        double[][] ytest = new double[testSize][];
        // train data
        for(int i = 0; i < trainingSize; i++) {
            if(i%2==0) {
                xtrain[i] = new double[]{i};
                ytrain[i] = new double[]{1};
            } else {
                xtrain[i] = new double[]{-i};
                ytrain[i] = new double[]{0};
            }
        }
        // test data
        for(int i = 0; i < testSize; i++) {
            if(i%3==0) {
                xtest[i] = new double[]{i};
                ytest[i] = new double[]{1};
            } else {
                xtest[i] = new double[]{-i};
                ytest[i] = new double[]{0};
            }
        }
        svm_model m = svmTrain(xtrain,ytrain,param);

        double[] ypred = svmPredict(xtest, m);

        //for (int i = 0; i < xtest.length; i++){
          //  System.out.println("(Actual:" + Arrays.toString(ytest[i]) + " Prediction:" + ypred[i] + ")");
        //}

    }
}
