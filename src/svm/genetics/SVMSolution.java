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
import java.util.Random;

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
    private static final Random rand = new Random(782);
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
        // constants
        p.probability = 1;
        p.svm_type = svm_parameter.C_SVC;
        p.cache_size = 20000;
        p.eps = 0.001;
        // randomly mutate
        if(rand.nextBoolean())p.gamma = rand.nextDouble();
        if(rand.nextBoolean())p.nu = rand.nextDouble();
        if(rand.nextBoolean())p.C = 10+rand.nextInt(500);
        if(rand.nextBoolean()&&rand.nextBoolean())p.kernel_type = rand.nextBoolean() ? (rand.nextBoolean() ? svm_parameter.RBF : svm_parameter.LINEAR) : (rand.nextBoolean() ? svm_parameter.SIGMOID : svm_parameter.POLY);
        return new SVMSolution(p,trainingData,validationData,technologies);
    }

    @Override
    public Solution crossover(Solution other_) {
        svm_parameter p = new svm_parameter();
        SVMSolution other = (SVMSolution)other_;
        svm_parameter otherParam = other.getParam();
        // constants
        p.probability = 1;
        p.svm_type = svm_parameter.C_SVC;
        p.cache_size = 20000;
        p.eps = 0.001;
        // randomly cross over
        p.gamma = rand.nextBoolean() ? param.gamma : otherParam.gamma;
        p.nu = rand.nextBoolean() ? param.nu : otherParam.nu;
        p.C = rand.nextBoolean() ? param.C : otherParam.C;
        p.kernel_type = rand.nextBoolean() ? param.kernel_type : otherParam.kernel_type;
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
