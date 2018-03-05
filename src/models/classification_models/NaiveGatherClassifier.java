package models.classification_models;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.graphs.*;
import graphical_modeling.model.learning.algorithms.BayesianLearningAlgorithm;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.model.nodes.Node;
import models.model_testing.GatherTechnologyScorer;
import models.model_testing.SplitModelData;
import org.nd4j.linalg.primitives.PairBackup;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier extends ClassificationAttr implements Serializable{
    private static final long serialVersionUID = 1l;
    static final File file = new File(Constants.DATA_FOLDER+"gather_bayesian_classifier.jobj");
    private static NaiveGatherClassifier defaultClassifier;
    private static final double defaultAlpha = 20d;

    protected BayesianNet bayesianNet;
    protected List<String> orderedTechnologies;
    protected List<String> orderedClassifications;
    protected double alpha;

    public String getName() {
        return "Bayesian Classifier";
    }

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

        List<Map<String,Integer>> assignments = getAssignments(trainingData,orderedTechnologies,patentToClassificationMap,orderedClassifications);

        // set data
        bayesianNet.setTrainingData(assignments);

        // add node
        Node cpcNode = bayesianNet.addNode("CPC",orderedClassifications.size());
        Node techNode = bayesianNet.addNode("Technology",orderedTechnologies.size());
        bayesianNet.connectNodes(cpcNode,techNode);
        bayesianNet.addFactorNode(null,cpcNode);
        bayesianNet.addFactorNode(null,techNode,cpcNode);

        // learn
        bayesianNet.applyLearningAlgorithm(new BayesianLearningAlgorithm(bayesianNet,alpha),1);
        System.out.println("Num assignments: "+assignments.size());
    }

    protected NaiveGatherClassifier(double alpha) {
        this.alpha=alpha;
    }

    @Override
    public List<PairBackup<String, Double>> attributesFor(Collection<String> portfolio, int limit) {
        Set<String> classifications = new HashSet<>();
        portfolio.forEach(token->classifications.addAll(Database.subClassificationsForPatent(token)));
        if(classifications.isEmpty()) return Collections.emptyList();

        double[] observation = getObservation(classifications,orderedClassifications);
        if(observation==null) return Collections.emptyList();

        FactorNode observedFactor = new FactorNode(observation,new String[]{"CPC"},new int[]{orderedClassifications.size()});
        observedFactor.reNormalize(new DivideByPartition());
        FactorNode condTechFactor = bayesianNet.findNode("Technology").getFactors().get(0);
        FactorNode result = observedFactor.multiply(condTechFactor).sumOut(new String[]{"CPC"});
        result.reNormalize(new DivideByPartition());
        List<PairBackup<String,Double>> values = new ArrayList<>();
        double[] array = result.getWeights();
        for(int i = 0; i < array.length; i++) {
            values.add(new PairBackup<>(orderedTechnologies.get(i),array[i]));
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

    protected double[] getObservation(Collection<String> observed, List<String> orderedClasses) {
        double[] observation = new double[orderedClasses.size()];
        Arrays.fill(observation,0d);
        AtomicBoolean found = new AtomicBoolean(false);
        observed.forEach(clazz->{
            int idx = orderedClasses.indexOf(clazz);
            if(idx>=0) {
                observation[idx]++;
                found.set(true);
            }
        });
        if(!found.get()) return null;
        return observation;
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
                    Map<String,Integer> assignment = new HashMap<>(2);
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
