package svm.genetics;

import genetics.Solution;
import genetics.SolutionCreator;
import org.deeplearning4j.berkeley.Pair;

import java.util.*;

import org.nd4j.linalg.api.ndarray.INDArray;
import svm.libsvm.svm_parameter;

/**
 * Created by Evan on 5/24/2017.
 */
public class SVMSolutionCreator implements SolutionCreator {
    protected Pair<double[][],double[][]> trainingData;
    protected Map<String,Collection<String>> validationData;
    protected List<String> technologies;
    private static final Random rand = new Random(69);
    private Map<String,INDArray> lookupTable;

    public SVMSolutionCreator(Pair<double[][],double[][]> trainingData, Map<String,Collection<String>> validationData, List<String> technologies, Map<String,INDArray> lookupTable) {
        this.trainingData=trainingData;
        this.validationData=validationData;
        this.lookupTable=lookupTable;
        this.technologies=technologies;
    }

    @Override
    public Collection<Solution> nextRandomSolutions(int n) {
        List<Solution> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            list.add(new SVMSolution(null,randomParameter(),trainingData,validationData,technologies,lookupTable));
        }
        return list;
    }


    public svm_parameter randomParameter() {
        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.cache_size = 20000;
        param.svm_type = svm_parameter.C_SVC;
        param.shrinking=0;
        param.kernel_type=3;
        param.eps = 0.001;
        param.p=0.5;
        param.C = 70d;

        param.coef0=(rand.nextDouble()*2d-1d);
        param.gamma =  Math.min(0.9999,Math.max(0.00001d,0.25+(rand.nextDouble()-0.5)*0.5));
        //param.nu = rand.nextDouble();
        param.C = rand.nextInt(200);
        //param.eps = 0.01 * rand.nextDouble();
        param.p=rand.nextDouble();
        param.shrinking=rand.nextBoolean()?0:1;
        param.kernel_type = rand.nextBoolean() ? (rand.nextBoolean() ? svm_parameter.RBF : svm_parameter.LINEAR) : (rand.nextBoolean() ? svm_parameter.SIGMOID : svm_parameter.POLY);

        return param;
    }
}
