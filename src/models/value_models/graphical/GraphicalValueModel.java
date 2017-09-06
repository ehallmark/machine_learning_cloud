package models.value_models.graphical;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NonNull;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.normalization.DivideByPartition;
import model.graphs.Graph;
import model.learning.algorithms.ExpectationMaximizationAlgorithm;
import model.nodes.FactorNode;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 8/30/17.
 */
public class GraphicalValueModel extends ValueAttr {
    private static final Random rand = new Random(569);
    protected Graph graph;
    protected double alpha;
    protected Item[] trainingItems;
    @Getter
    protected Map<String,List<String>> variableToValuesMap;
    protected String valueVariableName;
    @Getter
    protected String name;
    public GraphicalValueModel(String name, @NonNull Graph graph, double alpha, Item[] trainingItems, Map<String,List<String>> variableToValuesMap,  String valueVariableName) {
        this.graph=graph;
        this.variableToValuesMap = variableToValuesMap;
        this.alpha=alpha;
        this.trainingItems=trainingItems;
        this.valueVariableName=valueVariableName;
        this.name=name;
    }

    public void train() {
        graph.setTrainingData(createTrainingData(trainingItems));
        graph.applyLearningAlgorithm(new ExpectationMaximizationAlgorithm(graph,alpha, new BeliefPropagation()), 5);
    }

    @Override
    public double evaluate(Item token) {
        if(variableToValuesMap==null||graph==null) throw new RuntimeException("Must train the model first");
        Map<String,Integer> assignment = createAssignment(token,variableToValuesMap);
        graph.setCurrentAssignment(assignment);
        FactorNode resultingFactor = graph.variableElimination(new String[]{valueVariableName});
        Double val;
        if(resultingFactor.getCardinality()==1) {
            if(resultingFactor.getWeights().length!=2) throw new RuntimeException("Error in factor weights");
            resultingFactor.reNormalize(new DivideByPartition());
            val = resultingFactor.getWeights()[1]*100;
        } else {
            FactorNode finalFactor = resultingFactor.sumOut(Arrays.stream(resultingFactor.getVarLabels()).filter(var->!var.equals(valueVariableName)).toArray(size->new String[size]));
            if(finalFactor.getWeights().length!=2) throw new RuntimeException("Error in factor weights");
            finalFactor.reNormalize(new DivideByPartition());
            val = finalFactor.getWeights()[1]*100;
        }
        if(Double.isNaN(val)) val = defaultVal;
        return val;
    }

    private Collection<Map<String,Integer>> createTrainingData(Item[] items) {
        return Arrays.stream(items).parallel().map(item->{
            Map<String,Integer> assignment = createAssignment(item,variableToValuesMap);
            System.out.println("Assignment: "+new Gson().toJson(assignment));
            return assignment;
        }).filter(assignment->assignment!=null&&!assignment.isEmpty()).collect(Collectors.toSet());
    }

    private static Map<String,Integer> createAssignment(Item item, Map<String,List<String>> variableToValuesMap) {
        return item.getDataMap().entrySet().stream().filter(e->variableToValuesMap.containsKey(e.getKey())&&e.getValue()!=null).filter(e->{
            return Arrays.stream(e.getValue().toString().split("; ")).anyMatch(obj->variableToValuesMap.get(e.getKey()).contains(obj));
        }).collect(Collectors.toMap(e->e.getKey(),e->randomValue(Arrays.stream(e.getValue().toString().split("; ")).mapToInt(obj->variableToValuesMap.get(e.getKey()).indexOf(obj)).filter(i->i>=0))));
    }

    private static int randomValue(IntStream stream) {
        int[] ints = stream.toArray();
        return ints[rand.nextInt(ints.length)];
    }
}
