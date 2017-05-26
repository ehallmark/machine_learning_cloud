package ui_models.attributes.classification.genetics;

import genetics.Listener;
import genetics.Solution;
import svm.libsvm.svm_parameter;

import java.util.List;

/**
 * Created by Evan on 5/24/2017.
 */
public class TechTaggerSolutionListener implements Listener{

    @Override
    public void print(Solution _solution) {
        TechTaggerSolution solution = (TechTaggerSolution)_solution;
        List<Double> param = solution.getWeights();
        System.out.println("---------------------------");
        System.out.println("Score: "+solution.fitness());
        System.out.println("         CPC Model: "+param.get(0));
        System.out.println("    Bayesian Model: "+param.get(1));
        System.out.println("     PVector Model: "+param.get(2));
        System.out.println("         SVM Model: "+param.get(3));
        System.out.println("---------------------------");
    }
}
