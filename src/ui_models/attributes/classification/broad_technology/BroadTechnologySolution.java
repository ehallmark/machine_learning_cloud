package ui_models.attributes.classification.broad_technology;

import genetics.Solution;
import model_testing.GatherTechnologyScorer;
import ui_models.attributes.classification.ClassificationAttr;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/30/17.
 */
public class BroadTechnologySolution implements Solution {
    private static Random rand = new Random(59);
    private Map<String,String> broadTechMap;
    private List<ClassificationAttr> models;
    private double fitness = Double.MAX_VALUE;
    private Map<String,Collection<String>> validationData;

    public BroadTechnologySolution(Map<String,String> broadTechMap, List<ClassificationAttr> models, Map<String,Collection<String>> validationData) {
        this.broadTechMap=broadTechMap;
        this.models=models;
        this.validationData=validationData;
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        if(fitness < Double.MAX_VALUE) return; // already calculated
        fitness = models.stream()
                .collect(Collectors.averagingDouble(model-> model.getClassifications().stream().collect(Collectors.averagingDouble(tech->{
                    double accuracy = 0d;
                    if(validationData.containsKey(tech)) {
                        accuracy += GatherTechnologyScorer.scoreAssets(model, tech, validationData.get(tech), 3);
                    }
                    return accuracy;
                }))
        ));
    }

    @Override
    public Solution mutate() {
        return new BroadTechnologySolution(BroadTechnologySolutionCreator.randomMapMutation(broadTechMap,new ArrayList<>(broadTechMap.keySet())),models,validationData);
    }

    @Override
    public Solution crossover(Solution _other) {
        BroadTechnologySolution other = (BroadTechnologySolution)_other;
        Set<String> keys = new HashSet<>(broadTechMap.keySet());
        keys.addAll(other.broadTechMap.keySet());
        Map<String,String> newMap = keys.stream().collect(Collectors.toMap(key->key,key->{
            if(!broadTechMap.containsKey(key)) return other.broadTechMap.get(key);
            if(!other.broadTechMap.containsKey(key)) return broadTechMap.get(key);
            return rand.nextBoolean() ? broadTechMap.get(key) : other.broadTechMap.get(key);
        }));
        return new BroadTechnologySolution(newMap,models,validationData);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness());
    }

}
