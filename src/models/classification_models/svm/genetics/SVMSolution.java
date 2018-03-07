package models.classification_models.svm.genetics;

import models.genetics.Solution;
import lombok.Getter;
import models.model_testing.GatherTechnologyScorer;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import models.classification_models.GatherSVMClassifier;
import models.classification_models.svm.SVMHelper;
import models.classification_models.svm.libsvm.svm_parameter;
import models.classification_models.svm.libsvm.svm_model;
import models.classification_models.ClassificationAttr;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by Evan on 5/24/2017.
 */
public class SVMSolution implements Solution {
    protected svm_parameter param;
    protected Pair<double[][],double[][]> trainingData;
    protected Map<String,Collection<String>> validationData;
    @Getter
    protected svm_model model;
    protected Double fitness;
    protected File modelFile;
    private Map<String,INDArray> lookupTable;
    protected List<String> technologies;
    private static final Random rand = new Random(782);
    public SVMSolution(File modelFile, svm_parameter param, Pair<double[][],double[][]> trainingData, Map<String,Collection<String>> validationData, List<String> technologies, Map<String,INDArray> lookupTable) {
        this.param=param;
        this.modelFile=modelFile;
        this.trainingData=trainingData;
        this.validationData=validationData;
        this.lookupTable=lookupTable;
        this.technologies=technologies;
        this.fitness=null;
        //System.out.println("Training solution...");
        this.model = SVMHelper.svmTrain(trainingData.getFirst(),trainingData.getSecond(),param);
    }

    @Override
    public double fitness() {
        return fitness==null?0d:fitness;
    }

    @Override
    public void calculateFitness() {
        if(fitness == null) {
            ClassificationAttr svmTagger = new GatherSVMClassifier(model, technologies, modelFile,lookupTable);
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(svmTagger);
            fitness = scorer.accuracyOn(validationData, 3);
        }
    }

    @Override
    public Solution mutate() {
        svm_parameter p = new svm_parameter();
        // constants
        p.probability = 1;
        p.cache_size = 20000;
        p.svm_type = svm_parameter.C_SVC;
        p.shrinking=0;
        p.kernel_type=3;
        p.eps = 0.001;
        p.p=0.5;
        p.C = 70d;

        double delta = rand.nextDouble();
        // randomly mutate
        p.p= rand.nextBoolean() ? param.p : delta*rand.nextDouble() + (1d-delta)*param.p;
        p.gamma = Math.min(0.9999,Math.max(0.00001d,delta*(0.25+(rand.nextDouble()-0.5)*0.5) + (1d-delta)*param.gamma));
        delta=rand.nextDouble();
        p.C = delta*(rand.nextInt(200)) + (1d-delta)*param.C;
        //p.nu = rand.nextBoolean() ? param.nu : rand.nextDouble();
        delta=rand.nextDouble();
        p.coef0 = delta*(rand.nextDouble()*2d-1d) + (1d-delta)*param.coef0;
        //p.eps = rand.nextBoolean() ? param.eps : delta*0.01 * rand.nextDouble() + (1d-delta)*param.eps;
        p.shrinking = rand.nextBoolean() ? param.shrinking : rand.nextBoolean() ? 0 : 1;
        if(rand.nextBoolean()) p.kernel_type = rand.nextBoolean() ? (rand.nextBoolean() ? svm_parameter.RBF : svm_parameter.LINEAR) : (rand.nextBoolean() ? svm_parameter.SIGMOID : svm_parameter.POLY);
        return new SVMSolution(modelFile,p,trainingData,validationData,technologies,lookupTable);
    }

    @Override
    public Solution crossover(Solution other_) {
        svm_parameter p = new svm_parameter();
        SVMSolution other = (SVMSolution)other_;
        svm_parameter otherParam = other.getParam();
        // constants
        p.probability = 1;
        p.cache_size = 20000;
        p.svm_type = svm_parameter.C_SVC;
        p.shrinking=0;
        p.kernel_type=3;
        p.eps = 0.001;
        p.p=0.5;
        p.C = 70d;

        double delta = rand.nextDouble();
        // randomly cross over
        p.p= rand.nextBoolean() ? (param.p+otherParam.p)/2d : rand.nextBoolean() ? param.p : otherParam.p;
        p.gamma = param.gamma * delta + otherParam.gamma*(1d-delta);
        //p.nu = rand.nextBoolean() ? param.nu : otherParam.nu;
        delta = rand.nextDouble();
        p.C = delta*param.C + (1d-delta)*otherParam.C;
        delta = rand.nextDouble();
        p.coef0 = delta * param.coef0 + (1d-delta)*otherParam.coef0;
        //p.eps = rand.nextBoolean() ? (param.eps+otherParam.eps)/2d : rand.nextBoolean() ? param.eps : otherParam.eps;
        p.shrinking = rand.nextBoolean() ? param.shrinking : otherParam.shrinking;
        p.kernel_type = rand.nextBoolean() ? param.kernel_type : otherParam.kernel_type;
        return new SVMSolution(modelFile,p,trainingData,validationData,technologies,lookupTable);
    }

    public svm_parameter getParam() {
        return param;
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness());
    }
}
