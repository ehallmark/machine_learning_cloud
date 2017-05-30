package ui_models.attributes.classification.broad_technology;

import genetics.SolutionCreator;
import ui_models.attributes.classification.ClassificationAttr;

import java.util.*;

/**
 * Created by ehallmark on 5/30/17.
 */
public class BroadTechnologySolutionCreator implements SolutionCreator<BroadTechnologySolution> {
    private final List<ClassificationAttr> models;
    private final Map<String,Collection<String>> validationData;
    private final Map<String,String> startingBroadMap;
    private final List<String> broadTechnologies;
    private static final Random rand = new Random(69);

    public BroadTechnologySolutionCreator(Map<String,String> startingBroadMap, List<ClassificationAttr> models, Map<String,Collection<String>> validationData) {
        this.startingBroadMap=startingBroadMap;
        this.models=models;
        this.validationData=validationData;
        this.broadTechnologies=new ArrayList<>(startingBroadMap.keySet());
    }

    @Override
    public Collection<BroadTechnologySolution> nextRandomSolutions(int n) {
        List<BroadTechnologySolution> solutions = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            solutions.add(new BroadTechnologySolution(randomMapMutation(),models,validationData));
        }
        return solutions;
    }

    private Map<String,String> randomMapMutation() {
        return randomMapMutation(startingBroadMap,broadTechnologies);
    }

    public static Map<String,String> randomMapMutation(Map<String,String> startingBroadMap, List<String> broadTechnologies) {
        int n = startingBroadMap.size()/100 + rand.nextInt(startingBroadMap.size()/2);
        Map<String,String> mutation = new HashMap<>(startingBroadMap);
        for(int i = 0; i < n; i++) {
            String techToMove = broadTechnologies.get(rand.nextInt(broadTechnologies.size()));
            String techToReceive = broadTechnologies.get(rand.nextInt(broadTechnologies.size()));
            mutation.put(techToReceive, mutation.get(techToMove));
        }
        return mutation;
    }
}
