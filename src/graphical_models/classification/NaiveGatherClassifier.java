package graphical_models.classification;

import com.google.common.collect.Maps;
import model.functions.inference_methods.BeliefPropagation;
import model.functions.inference_methods.SamplingMethod;
import model.graphs.*;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.learning.algorithms.ExpectationMaximizationAlgorithm;
import model.learning.algorithms.MarkovLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.factory.Nd4j;
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

            // create assignment
            Map<String,Integer> assignment = new HashMap<>(orderedClassifications.size()+orderedTechnologies.size());
            orderedClassifications.forEach(cpc->{
                if(classes.contains(cpc)) assignment.put(cpc,1);
                else assignment.put(cpc,0);
            });
            orderedTechnologies.forEach(tech->{
                if(technologies.contains(tech)) assignment.put(tech,1);
                else assignment.put(tech,0);
            });

            assignments.add(assignment);
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

        System.out.println("Starting to create nodes.");

        // add node
        Node cpcNode = graph.addNode("CPC",orderedClassifications.size(),MathHelper.defaultValues(orderedClassifications.size()));
        graph.addFactorNode(null,cpcNode);
        orderedTechnologies.forEach(technology->{
            Node techNode = graph.addBinaryNode(technology);
            graph.connectNodes(cpcNode,techNode);
            graph.addFactorNode(null,techNode,cpcNode);
        });

        System.out.println("Finished adding nodes.");

        // learn
        graph.applyLearningAlgorithm(new BayesianLearningAlgorithm(graph,20d),1);


        // peek
        //graph.getFactorNodes().forEach(factor->{
        //    System.out.println(factor.toString());
        //});

        // example.put("Technology",5);

        for(int cpcIdx = 0; cpcIdx < orderedClassifications.size(); cpcIdx++) {
            System.out.println("Starting to classify...");
            Map<String,Integer> example = new HashMap<>();
            example.put("CPC",cpcIdx);
            graph.setCurrentAssignment(example);

            Iterator<Map<String,FactorNode>> chain = new MetropolisHastingsChain(graph,example);
            for(int i = 0; i < 100; i++) {
                chain.next();
                System.out.print("-");
            }
            System.out.println();
            System.out.println("Finished burn in");

            Map<String,FactorNode> result = chain.next();
            graph.setCurrentAssignment(example);
            String prediction = result.entrySet().stream()
                    .map(e->{
                        double value = MathHelper.expectedValue(e.getValue().getWeights(),new double[]{0d,1d});
                        return new Pair<>(e.getKey(),value);
                    }).max((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).get().getFirst();
            System.out.println("CPC "+orderedClassifications.get(cpcIdx)+": " + cpcTitle.get(orderedClassifications.get(cpcIdx)));
            System.out.println("Tech: " + prediction);
            System.out.println("Num assignments: "+assignments.size());

        }
        //System.out.println("Results: "+results.toString());
    }
}
