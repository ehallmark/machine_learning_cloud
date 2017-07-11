package user_interface.ui_models.attributes.classification.genetics;

import models.genetics.SolutionCreator;
import user_interface.ui_models.attributes.classification.*;

import java.util.*;

/**
 * Created by Evan on 5/24/2017.
 */
public class TechTaggerSolutionCreator implements SolutionCreator<TechTaggerSolution> {
    private Map<String,Collection<String>> validationData;
    private static final Random rand = new Random(69);
    private static List<ClassificationAttr> taggers;

    public static List<ClassificationAttr> getTaggers() {
        if(taggers==null) {
            taggers = Arrays.asList(
                    NaiveGatherClassifier.get(),
                    SimilarityGatherTechTagger.getParagraphVectorModel(),
                    //SimilarityGatherTechTagger.getCPCModel(),
                    //GatherSVMClassifier.getCPCModel(),
                    GatherSVMClassifier.getParagraphVectorModel()
            );
        }
        return taggers;
    }

    private List<ClassificationAttr> mTaggers;
    public TechTaggerSolutionCreator(Map<String,Collection<String>> validationData, List<ClassificationAttr> taggers) {
        this.mTaggers=taggers;
        this.validationData=validationData;
    }

    @Override
    public Collection<TechTaggerSolution> nextRandomSolutions(int n) {
        List<TechTaggerSolution> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            list.add(new TechTaggerSolution(mTaggers,randomParameter(),validationData));
        }
        return list;
    }


    public List<Double> randomParameter() {
        List<Double> weights = new ArrayList<>(mTaggers.size());
        for(int i = 0; i < mTaggers.size(); i++) {
            weights.add(rand.nextDouble());
        }
        return weights;
    }
}
