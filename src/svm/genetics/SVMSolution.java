package svm.genetics;

import genetics.Solution;
import model_testing.GatherTechnologyScorer;
import org.deeplearning4j.berkeley.Pair;
import svm.GatherSVMClassifier;
import svm.SVMHelper;
import svm.libsvm.svm_parameter;
import svm.libsvm.svm_model;
import ui_models.attributes.classification.ClassificationAttr;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/24/2017.
 */
public class SVMSolution implements Solution {
    private svm_parameter param;
    private Pair<double[][],double[][]> trainingData;
    private Map<String,Collection<String>> validationData;
    private svm_model model;
    private double fitness;
    private List<String> technologies;
    public SVMSolution(svm_parameter param, Pair<double[][],double[][]> trainingData, Map<String,Collection<String>> validationData, List<String> technologies) {
        this.param=param;
        this.trainingData=trainingData;
        this.validationData=validationData;
        this.technologies=technologies;
        this.fitness=0d;
        this.model = SVMHelper.svmTrain(trainingData.getFirst(),trainingData.getSecond(),param);
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        ClassificationAttr svmTagger = new GatherSVMClassifier(model,technologies);
        GatherTechnologyScorer scorer = new GatherTechnologyScorer(svmTagger);
        fitness = scorer.accuracyOn(validationData, 1);
    }

    @Override
    public Solution mutate() {
        svm_parameter p = new svm_parameter();
        // randomly mutate


        return new SVMSolution(p,trainingData,validationData,technologies);
    }

    @Override
    public Solution crossover(Solution other) {
        svm_parameter p = new svm_parameter();
        // randomly cross over


        return new SVMSolution(p,trainingData,validationData,technologies);
    }

    public svm_parameter getParam() {
        return param;
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness());
    }
}
