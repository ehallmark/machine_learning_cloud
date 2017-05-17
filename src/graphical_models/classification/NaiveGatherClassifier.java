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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier extends ClassificationAttr{
    protected BayesianNet bayesianNet;
    protected List<String> orderedTechnologies;
    protected List<String> orderedClassifications;
    public NaiveGatherClassifier() {
        Map<String,Collection<String>> gatherTraining = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Set<String>> patentToClassificationMap = Database.getPatentToClassificationMap();
        Map<String,Collection<String>> classificationToGatherPatentsMap = getClassificationToGatherPatentsMap(gatherTraining,patentToClassificationMap);
        orderedTechnologies = new ArrayList<>(gatherTraining.keySet());
        orderedClassifications = new ArrayList<>(classificationToGatherPatentsMap.keySet());

        List<Map<String,Integer>> assignments = getAssignments(gatherTraining,orderedTechnologies,patentToClassificationMap,orderedClassifications);

        System.out.println("Num assignments: "+assignments.size());
        bayesianNet = new BayesianNet();

        // set data
        bayesianNet.setTrainingData(assignments);

        System.out.println("Starting to create nodes.");

        // add node
        Node cpcNode = bayesianNet.addNode("CPC",orderedClassifications.size(),MathHelper.defaultValues(orderedClassifications.size()));
        Node techNode = bayesianNet.addNode("Technology",orderedTechnologies.size(),MathHelper.defaultValues(orderedTechnologies.size()));
        bayesianNet.connectNodes(cpcNode,techNode);
        bayesianNet.addFactorNode(null,cpcNode);
        bayesianNet.addFactorNode(null,techNode,cpcNode);

        System.out.println("Finished adding nodes.");

        // learn
        bayesianNet.applyLearningAlgorithm(new BayesianLearningAlgorithm(bayesianNet,20d),1);

    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        Set<String> classifications = new HashSet<>();
        portfolio.getTokens().forEach(token->classifications.addAll(Database.classificationsFor(token)));
        return classifications.stream().map(clazz->{
            Map<String,Integer> assignment = new HashMap<>();
            assignment.put("CPC",orderedClassifications.indexOf(clazz));
            CliqueTree cliqueTree = bayesianNet.createCliqueTree();
            cliqueTree.setCurrentAssignment(assignment);
            Map<String,FactorNode> results = cliqueTree.runBeliefPropagation(Arrays.asList("Technology"));
            double[] weights = results.get("Technology").getWeights();
            int maxIdx = MathHelper.indexOfMaxValue(weights);
            double maxValue = weights[maxIdx];
            return new Pair<>(orderedTechnologies.get(maxIdx),maxValue);
        }).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond())).entrySet().stream().collect(Collectors.groupingBy(e->e.getKey(),Collectors.summingDouble(e->e.getValue()))).entrySet()
                .stream().map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());
    }

    @Override
    public int numClassifications() {
        return orderedTechnologies.size();
    }

    @Override
    public Collection<String> getClassifications() {
        return new HashSet<>(orderedTechnologies);
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
            classes.forEach(cpc->{
                technologies.forEach(tech->{
                    // create assignment
                    Map<String,Integer> assignment = new HashMap<>(orderedClassifications.size()+orderedTechnologies.size());
                    assignment.put("CPC",orderedClassifications.indexOf(cpc));
                    assignment.put("Technology",orderedTechnologies.indexOf(tech));
                    assignments.add(assignment);

                });
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
        Map<String,String> cpcTitle = Database.getClassCodeToClassTitleMap().entrySet().stream().collect(Collectors.toMap(e->ClassCodeHandler.convertToLabelFormat(e.getKey()),e->e.getValue()));
        NaiveGatherClassifier classifier = new NaiveGatherClassifier();
        List<String> orderedClassifications = classifier.orderedClassifications;
        Graph graph = classifier.bayesianNet;
        boolean beliefProp=true;

        for(int cpcIdx = 0; cpcIdx < orderedClassifications.size(); cpcIdx++) {
            System.out.println("Starting to classify...");
            Map<String,Integer> example = new HashMap<>();
            example.put("CPC",cpcIdx);
            graph.setCurrentAssignment(example);

            Map<String,FactorNode> result;
            if(beliefProp) {
                CliqueTree cTree = graph.createCliqueTree();
                cTree.setCurrentAssignment(example);
                result = cTree.runBeliefPropagation(Arrays.asList("Technology"));
            } else {
                Iterator<Map<String, FactorNode>> chain = new MetropolisHastingsChain(graph, example);
                for (int i = 0; i < 100; i++) {
                    chain.next();
                    System.out.print("-");
                }
                System.out.println();
                System.out.println("Finished burn in");
                result=chain.next();
            }

            String prediction = classifier.orderedTechnologies.get(MathHelper.indexOfMaxValue(result.get("Technology").getWeights()));
            System.out.println();
            System.out.println("CPC "+orderedClassifications.get(cpcIdx)+": " + cpcTitle.get(orderedClassifications.get(cpcIdx)));
            System.out.println("Tech: " + prediction);
        }
        //System.out.println("Results: "+results.toString());
    }
}
