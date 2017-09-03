package models.value_models.bayesian;

import com.google.gson.Gson;
import elasticsearch.DataSearcher;
import lombok.Getter;
import lombok.NonNull;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.inference_methods.InferenceMethod;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.learning.algorithms.ExpectationMaximizationAlgorithm;
import model.nodes.FactorNode;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/30/17.
 */
public class BayesianValueModel extends ValueAttr {
    protected Graph graph;
    protected double alpha;
    protected Item[] trainingItems;
    protected Map<String,List<String>> variableToValuesMap;
    protected String valueVariableName;
    @Getter
    protected String name;
    public BayesianValueModel(String name, @NonNull Graph graph, double alpha, Item[] trainingItems, Map<String,List<String>> variableToValuesMap, @NonNull String valueVariableName) {
        this.graph=graph;
        this.variableToValuesMap = variableToValuesMap;
        this.alpha=alpha;
        this.trainingItems=trainingItems;
        this.valueVariableName=valueVariableName;
        this.name=name;
    }

    public void train() {
        graph.setTrainingData(createTrainingData(trainingItems));
        graph.applyLearningAlgorithm(new ExpectationMaximizationAlgorithm(graph,alpha, new BeliefPropagation()), 1);
    }

    @Override
    public double evaluate(Item token) {
        if(variableToValuesMap==null||graph==null) throw new RuntimeException("Must train the model first");
        Map<String,Integer> assignment = createAssignment(token,variableToValuesMap);
        graph.setCurrentAssignment(assignment);
        FactorNode resultingFactor = graph.variableElimination(new String[]{valueVariableName});
        if(resultingFactor.getCardinality()==1) {
            if(resultingFactor.getWeights().length!=2) throw new RuntimeException("Error in factor weights");
            resultingFactor.reNormalize(new DivideByPartition());
            return resultingFactor.getWeights()[1]*100;
        } else {
            FactorNode finalFactor = resultingFactor.sumOut(Arrays.stream(resultingFactor.getVarLabels()).filter(var->!var.equals(valueVariableName)).toArray(size->new String[size]));
            if(finalFactor.getWeights().length!=2) throw new RuntimeException("Error in factor weights");
            finalFactor.reNormalize(new DivideByPartition());
            return finalFactor.getWeights()[1]*100;
        }
    }

    private Collection<Map<String,Integer>> createTrainingData(Item[] items) {
        return Arrays.stream(items).parallel().map(item->{
            Map<String,Integer> assignment = createAssignment(item,variableToValuesMap);
            System.out.println("Assignment: "+new Gson().toJson(assignment));
            return assignment;
        }).filter(assignment->assignment!=null&&assignment.size()==variableToValuesMap.size()).collect(Collectors.toSet());
    }

    private static Map<String,Integer> createAssignment(Item item, Map<String,List<String>> variableToValuesMap) {
        return item.getDataMap().entrySet().stream().filter(e->variableToValuesMap.containsKey(e.getKey())&&e.getValue()!=null&&variableToValuesMap.get(e.getKey()).contains(e.getValue().toString()))
                .collect(Collectors.toMap(e->e.getKey(),e->variableToValuesMap.get(e.getKey()).indexOf(e.getValue().toString())));
    }
}
