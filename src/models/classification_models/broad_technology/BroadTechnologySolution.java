package models.classification_models.broad_technology;

import models.genetics.Solution;
import lombok.Getter;
import models.model_testing.GatherClassificationOptimizer;
import models.model_testing.GatherTechnologyScorer;
import models.model_testing.SplitModelData;
import models.classification_models.ClassificationAttr;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/30/17.
 */
public class BroadTechnologySolution implements Solution {
    private static Random rand = new Random(59);
    @Getter
    private Map<String,String> broadTechMap;
    @Getter
    private List<ClassificationAttr> models;
    private Double fitness;
    private GatherClassificationOptimizer optimizer;

    public BroadTechnologySolution(Map<String,String> broadTechMap, List<ClassificationAttr> models, GatherClassificationOptimizer optimizer) {
        this.broadTechMap=broadTechMap;
        this.models=models;
        this.optimizer=optimizer;
        this.fitness=null;
    }

    @Override
    public double fitness() {
        return fitness==null?0d:fitness;
    }

    @Override
    public void calculateFitness() {
        if(fitness != null) return; // already calculated
        // train new models
        models = optimizer.duplicateAndTrainModels(models,broadTechMap);
        Map<String,Collection<String>> validationData = SplitModelData.regroupData(optimizer.getRawValidation1Data(),broadTechMap);

        // calculate fitness
        fitness = models.stream()
                .collect(Collectors.averagingDouble(model-> {
                    GatherTechnologyScorer scorer = new GatherTechnologyScorer(model);
                    return scorer.accuracyOn(validationData,3);
                }
        ));
    }

    @Override
    public Solution mutate() {
        return new BroadTechnologySolution(BroadTechnologySolutionCreator.randomMapMutation(broadTechMap,new ArrayList<>(broadTechMap.keySet())),models,optimizer);
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
        return new BroadTechnologySolution(newMap,models,optimizer);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness());
    }

}
