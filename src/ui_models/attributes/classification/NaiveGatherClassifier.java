package ui_models.attributes.classification;

import model.functions.normalization.DivideByPartition;
import model.graphs.*;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import model_testing.GatherTechnologyScorer;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import ui_models.portfolios.AbstractPortfolio;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier implements ClassificationAttr, Serializable{
    private static final long serialVersionUID = 1l;
    static final File file = new File("gather_bayesian_classifier.jobj");
    private static NaiveGatherClassifier defaultClassifier;
    private static final double defaultAlpha = 20d;

    private  INDArray preallocatedArray;
    protected BayesianNet bayesianNet;
    protected List<String> orderedTechnologies;
    protected List<String> orderedClassifications;
    protected double alpha;


    @Override
    public void save() {
        Database.trySaveObject(this,file);
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        NaiveGatherClassifier bestModel = this;
        GatherTechnologyScorer current = new GatherTechnologyScorer(this);
        double bestScoreSoFar = current.accuracyOn(validationData,3);
        for(int i = 0; i < 5; i++) {
            NaiveGatherClassifier solution = new NaiveGatherClassifier(alpha*Math.exp(2.5-i));
            solution.train(trainingData);
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(solution);
            double score = scorer.accuracyOn(validationData,3);
            if(score > bestScoreSoFar) {
                bestModel = solution;
                bestScoreSoFar=score;
            }
        }
        System.out.println("Best Alpha: "+bestModel.alpha);
        System.out.println("  Score: "+bestScoreSoFar);
        return bestModel;
    }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new NaiveGatherClassifier(alpha);
    }

    public static NaiveGatherClassifier get() {
        if(defaultClassifier==null) {
            defaultClassifier=(NaiveGatherClassifier)Database.tryLoadObject(file);
            if(defaultClassifier==null) {
                defaultClassifier=new NaiveGatherClassifier(defaultAlpha);
                defaultClassifier.train(SplitModelData.getBroadDataMap(SplitModelData.trainFile));
                defaultClassifier.save();
            }
        }
        return defaultClassifier;
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        Map<String,Set<String>> patentToClassificationMap = Database.getPatentToClassificationMap();
        Map<String,Collection<String>> classificationToGatherPatentsMap = getClassificationToGatherPatentsMap(trainingData,patentToClassificationMap);
        orderedTechnologies = new ArrayList<>(trainingData.keySet());
        orderedClassifications = new ArrayList<>(classificationToGatherPatentsMap.keySet());
        bayesianNet = new BayesianNet();
        preallocatedArray = Nd4j.create(orderedTechnologies.size());

        List<Map<String,Integer>> assignments = getAssignments(trainingData,orderedTechnologies,patentToClassificationMap,orderedClassifications);

        // set data
        bayesianNet.setTrainingData(assignments);

        // add node
        Node cpcNode = bayesianNet.addNode("CPC",orderedClassifications.size(),Nd4j.create(orderedClassifications.size()));
        Node techNode = bayesianNet.addNode("Technology",orderedTechnologies.size(),Nd4j.create(orderedTechnologies.size()));
        bayesianNet.connectNodes(cpcNode,techNode);
        bayesianNet.addFactorNode(null,cpcNode);
        bayesianNet.addFactorNode(null,techNode,cpcNode);

        // learn
        bayesianNet.applyLearningAlgorithm(new BayesianLearningAlgorithm(bayesianNet,alpha),1);
        System.out.println("Num assignments: "+assignments.size());
    }

    private NaiveGatherClassifier(double alpha) {
        this.alpha=alpha;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        Set<String> classifications = new HashSet<>();
        portfolio.getTokens().forEach(token->classifications.addAll(Database.subClassificationsForPatent(token)));
        if(classifications.isEmpty()) return Collections.emptyList();

        double[] observation = new double[orderedClassifications.size()];
        Arrays.fill(observation,0d);
        AtomicBoolean found = new AtomicBoolean(false);
        classifications.forEach(clazz->{
           int idx = orderedClassifications.indexOf(clazz);
           if(idx>=0) {
               observation[idx]++;
               found.set(true);
           }
        });
        if(!found.get()) return Collections.emptyList();

        FactorNode observedFactor = new FactorNode(Nd4j.create(observation),new String[]{"CPC"},new int[]{orderedClassifications.size()},bayesianNet.findNode("CPC").getValueMap());
        observedFactor.reNormalize(new DivideByPartition());
        FactorNode condTechFactor = bayesianNet.findNode("Technology").getFactors().get(0);
        FactorNode result = observedFactor.multiply(condTechFactor).sumOut(new String[]{"CPC"},preallocatedArray);
        result.reNormalize(new DivideByPartition());
        List<Pair<String,Double>> values = new ArrayList<>();
        double[] array = result.getWeights().data().asDouble();
        for(int i = 0; i < array.length; i++) {
            values.add(new Pair<>(orderedTechnologies.get(i),array[i]));
        }
        return values.stream().sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit).collect(Collectors.toList());
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
        double alpha = 20d;
        NaiveGatherClassifier classifier = new NaiveGatherClassifier(alpha);
        classifier.train(SplitModelData.getBroadDataMap(SplitModelData.trainFile));
        // save
        classifier.save();

        classifier = get();
        System.out.println("Num tech: "+classifier.orderedTechnologies.size());

    }
}
