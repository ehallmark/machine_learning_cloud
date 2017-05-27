package ui_models.attributes.classification.genetics;

import genetics.Solution;
import genetics.SolutionCreator;
import lombok.Getter;
import ui_models.attributes.classification.*;

import java.util.*;

/**
 * Created by Evan on 5/24/2017.
 */
public class TechTaggerSolutionCreator implements SolutionCreator {
    private Map<String,Collection<String>> validationData;
    private static final Random rand = new Random(69);
    @Getter
    private static final List<ClassificationAttr> taggers = Arrays.asList(CPCGatherTechTagger.get(), NaiveGatherClassifier.get(), SimilarityGatherTechTagger.getAIModelTagger(), GatherSVMClassifier.get());
    @Getter
    private static final List<Double> weights = Arrays.asList(0.7,0.95,0.03,0.23);
    public TechTaggerSolutionCreator(Map<String,Collection<String>> validationData) {
        this.validationData=validationData;
    }

    @Override
    public Collection<Solution> nextRandomSolutions(int n) {
        List<Solution> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            list.add(new TechTaggerSolution(taggers,randomParameter(),validationData));
        }
        return list;
    }


    public List<Double> randomParameter() {
        List<Double> weights = new ArrayList<>(taggers.size());
        for(int i = 0; i < taggers.size(); i++) {
            weights.add(rand.nextDouble());
        }
        return weights;
    }
}
