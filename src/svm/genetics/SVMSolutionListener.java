package svm.genetics;

import genetics.Listener;
import genetics.Solution;
import genetics.lead_development.CompanySolution;
import svm.libsvm.svm_parameter;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/24/2017.
 */
public class SVMSolutionListener implements Listener{

    @Override
    public void print(Solution _solution) {
        SVMSolution solution = (SVMSolution)_solution;
        svm_parameter param = solution.getParam();
        System.out.println("---------------------------");
        System.out.println("Score: "+solution.fitness());
        System.out.println("          C: "+param.C);
        System.out.println("      Gamma:"+param.gamma);
        //System.out.println("         Nu: "+param.nu);
        //System.out.println("     Kernel: "+param.kernel_type);
        System.out.println("          p: "+param.p);
        System.out.println("      coef0: "+param.coef0);
        //System.out.println("  Shrinking: "+param.shrinking);
        System.out.println("        eps: "+param.eps);
        System.out.println("---------------------------");
    }
}
