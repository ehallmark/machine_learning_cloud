package graphical_modeling.model.learning.distributions;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.util.MathHelper;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 4/28/17.
 */
public class Dirichlet implements Distribution {
    private static final double EPSILON = 0.00001;
    protected double alpha;
    protected FactorNode factor;
    protected AtomicInteger seenSoFar;
    protected double[] weightsCopy;
    protected double[] previousWeights;
    protected boolean converged;
    @Getter
    protected double score;
    @Getter
    @Setter
    protected double learningRate = 0.01d;

    public Dirichlet(FactorNode factor, double alpha) {
        this.alpha=alpha;
        this.factor=factor;
        this.converged=false;
        this.seenSoFar=new AtomicInteger(0);
        this.score=Double.MAX_VALUE;
    }

    public boolean getConverged() {
        return converged;
    }

    @Override
    public void train(Map<String,Integer> assignmentMap) {
        int[] assignment = new int[factor.getNumVariables()];
        factor.getVarToIndexMap().forEach((var,idx)->{
            Integer varAssignment = assignmentMap.get(var);
            if(varAssignment==null) throw new RuntimeException("Null assignment");
            assignment[idx]=varAssignment;
        });

        int idx = factor.assignmentToIndex(assignment);

        weightsCopy[idx]++;

        // increment final counter
        seenSoFar.getAndIncrement();
    }

    @Override
    public void updateFactorWeights() {
        previousWeights=factor.getWeights();

        factor.setWeights(Arrays.copyOf(weightsCopy,weightsCopy.length));
        factor.reNormalize(new DivideByPartition());

        // Check for convergence
        if (previousWeights != null) {
            score = MathHelper.euclideanDistance(factor.getWeights(),previousWeights);
            score = Math.abs(score);
            updateConvergedStatus();
        }

    }

    private void updateConvergedStatus() {
        if(!converged)converged = (score < EPSILON);
    }

    @Override
    public void initialize() {
        weightsCopy=new double[factor.getNumAssignments()];
        Arrays.fill(weightsCopy,alpha);
    }
}
