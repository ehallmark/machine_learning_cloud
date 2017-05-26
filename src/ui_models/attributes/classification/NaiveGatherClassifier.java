package ui_models.attributes.classification;

import model.functions.normalization.DivideByPartition;
import model.graphs.*;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import ui_models.portfolios.AbstractPortfolio;
import util.MathHelper;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier extends ClassificationAttr implements Serializable{
    static final File file = new File("gather_bayesian_classifier.jobj");
    private static NaiveGatherClassifier defaultClassifier;

    protected BayesianNet bayesianNet;
    protected List<String> orderedTechnologies;
    protected List<String> orderedClassifications;

    public static NaiveGatherClassifier get() {
        if(defaultClassifier==null) {
            defaultClassifier=(NaiveGatherClassifier)Database.tryLoadObject(file);
        }
        return defaultClassifier;
    }

    private NaiveGatherClassifier() {
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
        double[] observation = new double[orderedClassifications.size()];
        Arrays.fill(observation,0d);
        classifications.forEach(clazz->{
           int idx = orderedClassifications.indexOf(clazz);
           if(idx>=0) {
               observation[idx]++;
           }
        });
        FactorNode observedFactor = new FactorNode(observation,new String[]{"CPC"},new int[]{orderedClassifications.size()},bayesianNet.findNode("CPC").getValueMap());
        observedFactor.reNormalize(new DivideByPartition());
        FactorNode condTechFactor = bayesianNet.findNode("Technology").getFactors().get(0);
        FactorNode result = observedFactor.multiply(condTechFactor).sumOut(new String[]{"CPC"});
        result.reNormalize(new DivideByPartition());
        List<Pair<String,Double>> values = new ArrayList<>();
        for(int i = 0; i < result.getWeights().length; i++) {
            values.add(new Pair<>(orderedTechnologies.get(i),result.getWeights()[i]));
        }
        return values.stream().sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit).collect(Collectors.toList());
        /*return classifications.stream().map(clazz->{
            Map<String,Integer> assignment = new HashMap<>();
            int classIdx = orderedClassifications.indexOf(clazz);
            if(classIdx>=0) {
                assignment.put("CPC", classIdx);
                CliqueTree cliqueTree = bayesianNet.createCliqueTree();
                cliqueTree.setCurrentAssignment(assignment);
                Map<String, FactorNode> results = cliqueTree.runBeliefPropagation(Arrays.asList("Technology"));
                double[] weights = results.get("Technology").getWeights();
                int maxIdx = MathHelper.indexOfMaxValue(weights);
                double maxValue = weights[maxIdx];
                return new Pair<>(orderedTechnologies.get(maxIdx), maxValue);
            } else return null;
        }).filter(p->p!=null).collect(Collectors.groupingBy(e->e.getFirst(),Collectors.summingDouble(e->e.getSecond()))).entrySet()
                .stream().map(e->new Pair<>(e.getKey(),e.getValue())).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit).collect(Collectors.toList());
         */
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
        NaiveGatherClassifier classifier = new NaiveGatherClassifier();
        // save
        Database.trySaveObject(classifier,file);
    }
}
