package ui_models.attributes.classification.genetics;

import genetics.Solution;
import lombok.Getter;
import model_testing.GatherTechnologyScorer;
import org.deeplearning4j.berkeley.Pair;
import svm.SVMHelper;
import svm.libsvm.svm_model;
import svm.libsvm.svm_parameter;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.GatherSVMClassifier;
import ui_models.attributes.classification.TechTaggerNormalizer;

import java.util.*;

/**
 * Created by Evan on 5/24/2017.
 */
public class TechTaggerSolution implements Solution {
    private Map<String,Collection<String>> validationData;
    private Double fitness;
    private List<ClassificationAttr> taggers;
    @Getter
    private List<Double> weights;
    private static final Random rand = new Random(782);
    public TechTaggerSolution(List<ClassificationAttr> taggers, List<Double> weights, Map<String,Collection<String>> validationData) {
        this.validationData=validationData;
        this.fitness=null;
        this.weights=weights;
        this.taggers=taggers;
    }

    @Override
    public double fitness() {
        return fitness==null?Double.MIN_VALUE:fitness;
    }

    @Override
    public void calculateFitness() {
        if(fitness == null) {
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(new TechTaggerNormalizer(taggers,weights));
            fitness = scorer.accuracyOn(validationData, 5);
        }
    }

    @Override
    public Solution mutate() {
        List<ClassificationAttr> newTaggers = new ArrayList<>(taggers);
        List<Double> newWeights = new ArrayList<>(weights);
        newWeights.set(rand.nextInt(newWeights.size()), rand.nextDouble());
        return new TechTaggerSolution(newTaggers,newWeights,validationData);
    }

    @Override
    public Solution crossover(Solution other_) {
        List<ClassificationAttr> newTaggers = new ArrayList<>(taggers);
        List<Double> newWeights = new ArrayList<>(weights.size());
        List<Double> otherWeights = ((TechTaggerSolution) other_).weights;
        for(int i = 0; i < weights.size(); i++) {
            double delta = rand.nextDouble();
            newWeights.add(otherWeights.get(i)*delta + weights.get(i)*(1d-delta));
        }
        return new TechTaggerSolution(newTaggers,newWeights,validationData);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness());
    }
}
