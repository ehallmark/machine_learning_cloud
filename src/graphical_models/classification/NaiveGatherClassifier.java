package graphical_models.classification;

import com.google.common.collect.Maps;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.inference_methods.SamplingMethod;
import model.graphs.BayesianNet;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.learning.algorithms.ExpectationMaximizationAlgorithm;
import model.learning.algorithms.MarkovLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import tools.ClassCodeHandler;
import ui_models.attributes.classification.CPCGatherTechTagger;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.helper.BuildCPCToGatherStatistics;
import ui_models.portfolios.AbstractPortfolio;
import util.MathHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier extends ClassificationAttr{
    protected BayesianNet bayesianNet;

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        return null;
    }

    @Override
    public int numClassifications() {
        return 0;
    }

    @Override
    public Collection<String> getClassifications() {
        return null;
    }


    public static Map<String,Collection<String>> getClassificationToGatherPatentsMap(Map<String,Collection<String>> gatherTraining, Map<String,Set<String>> patentToClassificationMap) {
        Set<String> patents = new HashSet<>();
        gatherTraining.forEach((tech,pSet)->patents.addAll(pSet));
        Map<String,Collection<String>> map = new HashMap<>();
        patents.forEach(patent->{
            if(patentToClassificationMap.containsKey(patent)) {
                Set<String> cpcs = patentToClassificationMap.get(patent);
                cpcs.forEach(cpc->{
                    if(map.containsKey(cpc)) {
                        map.get(cpc).add(patent);
                    } else {
                        Set<String> set = new HashSet<>();
                        set.add(patent);
                        map.put(cpc,set);
                    }
                });
            }
        });
        return map;
    }

    public static List<Map<String,Integer>> getAssignments(Map<String,Collection<String>> gatherTraining, List<String> orderedTechnologies, Map<String,Set<String>> patentToClassificationMap, List<String> orderedClassifications) {
        List<Map<String,Integer>> assignments = new ArrayList<>();
        Map<String,Collection<String>> patentsToTech = invert(gatherTraining);
        patentsToTech.forEach((patent,technologies)->{
            // classes
            Collection<String> classes = patentToClassificationMap.getOrDefault(patent, Collections.emptySet());

            technologies.forEach(tech->{
                if(!classes.isEmpty()) {
                    classes.forEach(clazz -> {
                        Map<String, Integer> assignment = new HashMap<>();
                        assignment.put("Technology", orderedTechnologies.indexOf(tech));
                        assignment.put("CPC", orderedClassifications.indexOf(clazz));
                        assignments.add(assignment);
                    });
                }
            });
        });
        return assignments;
    }

    public static Map<String,Collection<String>> invert(Map<String,Collection<String>> map) {
        Map<String,Collection<String>> invert = new HashMap<>();
        map.forEach((k,v)->{
            v.forEach(x->{
                if(invert.containsKey(x)) {
                    invert.get(x).add(k);
                } else {
                    Set<String> s = new HashSet<>();
                    s.add(k);
                    invert.put(x,s);
                }
            });
        });
        return invert;
    }

    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTraining = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Set<String>> patentToClassificationMap = Database.getPatentToClassificationMap();
        Map<String,Collection<String>> classificationToGatherPatentsMap = getClassificationToGatherPatentsMap(gatherTraining,patentToClassificationMap);
        List<String> orderedTechnologies = new ArrayList<>(gatherTraining.keySet());
        List<String> orderedClassifications = new ArrayList<>(classificationToGatherPatentsMap.keySet());
        Map<String,String> cpcTitle = Database.getClassCodeToClassTitleMap().entrySet().stream().collect(Collectors.toMap(e->ClassCodeHandler.convertToLabelFormat(e.getKey()),e->e.getValue()));

        List<Map<String,Integer>> assignments = getAssignments(gatherTraining,orderedTechnologies,patentToClassificationMap,orderedClassifications);

        System.out.println("Num assignments: "+assignments.size());
        Graph graph = new BayesianNet();

        // set data
        graph.setTrainingData(assignments);

        // add nodes
        Node techNode = graph.addNode("Technology",orderedTechnologies.size(), MathHelper.defaultValues(orderedTechnologies.size()));
        Node cpcNode = graph.addNode("CPC",orderedClassifications.size(), MathHelper.defaultValues(orderedClassifications.size()));
        //graph.addFactorNode(null,cpcNode);
        graph.connectNodes(cpcNode,techNode);
        graph.addFactorNode(null,techNode,cpcNode);
        //graph.addFactorNode(null,cpcNode);

        // learn
        graph.applyLearningAlgorithm(new ExpectationMaximizationAlgorithm(graph,1d, new BeliefPropagation()) {
            protected Map<String, Integer> handleAssignment(Map<String, Integer> assignment, Graph graph) {
                System.out.println("HANDLE ASSIGNMENT: "+graph.getFactorNodes().stream().collect(Collectors.summingDouble(d->MathHelper.sum(d.getWeights()))));
                return super.handleAssignment(assignment,graph);
            }
        },5000);

        // peek
        //graph.getFactorNodes().forEach(factor->{
        //    System.out.println(factor.toString());
        //});

        // example.put("Technology",5);

        for(int cpcIdx = 0; cpcIdx < orderedClassifications.size(); cpcIdx++) {
            CliqueTree cliqueTree = graph.createCliqueTree();
            Map<String,Integer> example = new HashMap<>();
            example.put("CPC", cpcIdx);
            cliqueTree.setCurrentAssignment(example);
            graph.setCurrentAssignment(example);
            FactorNode results = cliqueTree.runBeliefPropagation(Arrays.asList("Technology")).get("Technology");
            int techIdx = MathHelper.indexOfMaxValue(results.getWeights());
            String tech = orderedTechnologies.get(techIdx);
            System.out.println("CPC "+orderedClassifications.get(cpcIdx)+": " + cpcTitle.get(orderedClassifications.get(cpcIdx)));
            System.out.println("Tech: " + tech);
            System.out.println("Num assignments: "+assignments.size());

        }
        //System.out.println("Results: "+results.toString());
    }
}
