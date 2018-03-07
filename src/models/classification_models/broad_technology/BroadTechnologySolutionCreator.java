package models.classification_models.broad_technology;

import models.genetics.SolutionCreator;
import models.model_testing.GatherClassificationOptimizer;
import models.classification_models.ClassificationAttr;

import java.util.*;

/**
 * Created by ehallmark on 5/30/17.
 */
public class BroadTechnologySolutionCreator implements SolutionCreator<BroadTechnologySolution> {
    private final List<ClassificationAttr> models;
    private final GatherClassificationOptimizer optimizer;
    private final Map<String,String> startingBroadMap;
    private final List<String> rawTechnologies;
    private static final Random rand = new Random(69);

    public BroadTechnologySolutionCreator(Map<String,String> startingBroadMap, List<ClassificationAttr> models, GatherClassificationOptimizer optimizer) {
        this.startingBroadMap=startingBroadMap;
        this.models=models;
        this.optimizer=optimizer;
        this.rawTechnologies=new ArrayList<>(startingBroadMap.keySet());
    }

    @Override
    public Collection<BroadTechnologySolution> nextRandomSolutions(int n) {
        List<BroadTechnologySolution> solutions = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            solutions.add(new BroadTechnologySolution(randomMapMutation(),models,optimizer));
        }
        return solutions;
    }

    private Map<String,String> randomMapMutation() {
        return randomMapMutation(startingBroadMap,rawTechnologies);
    }

    public static Map<String,String> randomMapMutation(Map<String,String> startingBroadMap, List<String> rawTechnologies) {
        int n = 1;
        Map<String,String> mutation = new HashMap<>(startingBroadMap);
        for(int i = 0; i < n; i++) {
            String techToMove = rawTechnologies.get(rand.nextInt(rawTechnologies.size()));
            String techToReceive = rawTechnologies.get(rand.nextInt(rawTechnologies.size()));
            mutation.put(techToReceive, mutation.get(techToMove));
        }
        return mutation;
    }
}
