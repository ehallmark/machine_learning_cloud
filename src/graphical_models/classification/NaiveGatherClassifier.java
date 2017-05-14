package graphical_models.classification;

import com.google.common.collect.Maps;
import model.graphs.BayesianNet;
import model.graphs.CliqueTree;
import model.graphs.Graph;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
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
            Collection<String> classes = patentToClassificationMap.get(patent);

            technologies.forEach(tech->{
                Map<String,Integer> assignment = new HashMap<>();
                assignment.put("Technology",orderedTechnologies.indexOf(tech));
                assignment.putAll(orderedClassifications.stream().collect(Collectors.toMap(clazz->clazz,clazz->{
                    return classes.contains(clazz) ? 1 : 0;
                })));
                assignments.add(assignment);
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

        List<Map<String,Integer>> assignments = getAssignments(gatherTraining,orderedTechnologies,patentToClassificationMap,orderedClassifications);

        Graph graph = new BayesianNet();

        // set data
        graph.setTrainingData(assignments);

        // add nodes
        Node techNode = graph.addNode("Technology",orderedTechnologies.size(), MathHelper.defaultValues(orderedTechnologies.size()));
        orderedClassifications.forEach(cpc->{
            Node cpcNode = graph.addBinaryNode(cpc);
            graph.addFactorNode(null,cpcNode);
            graph.connectNodes(cpc,"Technology");
            graph.addFactorNode(null,techNode,cpcNode);
        });

        // learn
        graph.applyLearningAlgorithm(new BayesianLearningAlgorithm(graph,20d),1);

        CliqueTree cliqueTree = graph.createCliqueTree();
        Map<String,FactorNode> results = cliqueTree.runBeliefPropagation(Arrays.asList("Technology"));

        System.out.println("Results: ");
        results.forEach((k,v)->{
            System.out.println("  "+k+": "+v.toString());
        });
    }
}
