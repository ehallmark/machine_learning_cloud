package svm.genetics;

import genetics.Solution;
import genetics.SolutionCreator;
import org.deeplearning4j.berkeley.Pair;

import java.util.*;

import svm.libsvm.svm_parameter;

/**
 * Created by Evan on 5/24/2017.
 */
public class SVMSolutionCreator implements SolutionCreator {
    private Pair<double[][],double[][]> trainingData;
    private Map<String,Collection<String>> validationData;
    private List<String> technologies;
    private static final Random rand = new Random(69);

    public SVMSolutionCreator(Pair<double[][],double[][]> trainingData, Map<String,Collection<String>> validationData, List<String> technologies) {
        this.trainingData=trainingData;
        this.validationData=validationData;
        this.technologies=technologies;
    }

    @Override
    public Collection<Solution> nextRandomSolutions(int n) {
        List<Solution> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            list.add(new SVMSolution(randomParameter(),trainingData,validationData,technologies));
        }
        return list;
    }


    public svm_parameter randomParameter() {
        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.gamma = rand.nextDouble();
        param.nu = rand.nextDouble();
        param.C = 10+rand.nextInt(1000);
        param.svm_type = svm_parameter.C_SVC;
        //param.kernel_type = rand.nextBoolean() ? (rand.nextBoolean() ? svm_parameter.RBF : svm_parameter.LINEAR) : (rand.nextBoolean() ? svm_parameter.SIGMOID : svm_parameter.POLY);
        param.kernel_type=0; // linear
        param.cache_size = 20000;
        param.eps = 0.001;

        return param;
    }
}
