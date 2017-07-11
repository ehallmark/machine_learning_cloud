package user_interface.ui_models.attributes.classification;

import models.classification_models.WIPOHelper;
import model.functions.normalization.DivideByPartition;
import model.graphs.BayesianNet;
import model.learning.algorithms.BayesianLearningAlgorithm;
import model.nodes.FactorNode;
import model.nodes.Node;
import models.model_testing.GatherTechnologyScorer;
import models.model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifierWithWIPO extends NaiveGatherClassifier{
    private static final long serialVersionUID = 1l;
    static final File file = new File("gather_bayesian_classifier_with_wipo.jobj");
    private static NaiveGatherClassifierWithWIPO defaultClassifier;
    private static final double defaultAlpha = 20d;

    protected List<String> orderedWIPO;

    public String getName() {
        return "Bayesian CPC and WIPO Classifier";
    }

    @Override
    public void save() {
        Database.trySaveObject(this,file);
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        NaiveGatherClassifierWithWIPO bestModel = this;
        GatherTechnologyScorer current = new GatherTechnologyScorer(this);
        double bestScoreSoFar = current.accuracyOn(validationData,3);
        for(int i = 0; i < 5; i++) {
            NaiveGatherClassifierWithWIPO solution = new NaiveGatherClassifierWithWIPO(alpha*Math.exp(2.5-i));
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
        return new NaiveGatherClassifierWithWIPO(alpha);
    }

    public static NaiveGatherClassifierWithWIPO get() {
        if(defaultClassifier==null) {
            defaultClassifier=(NaiveGatherClassifierWithWIPO)Database.tryLoadObject(file);
            if(defaultClassifier==null) {
                defaultClassifier=new NaiveGatherClassifierWithWIPO(defaultAlpha);
                defaultClassifier.train(SplitModelData.getBroadDataMap(SplitModelData.trainFile));
                defaultClassifier.save();
            }
        }
        return defaultClassifier;
    }

    public static List<Map<String,Integer>> getAssignments(Map<String,Collection<String>> gatherTraining, List<String> orderedTechnologies, Map<String,Set<String>> patentToClassificationMap, List<String> orderedClassifications, Map<String,String> patentToWIPOMap, List<String> orderedWIPO) {
        List<Map<String,Integer>> assignments = new ArrayList<>();
        Map<String,Collection<String>> patentsToTech = invert(gatherTraining);
        patentsToTech.forEach((patent,technologies)->{
            // classes
            Collection<String> classes = patentToClassificationMap.getOrDefault(patent, Collections.emptySet());
            String wipo = patentToWIPOMap.getOrDefault(patent, null);
            if(wipo!=null) {
                final int wipoIdx = orderedWIPO.indexOf(wipo);
                classes.forEach(cpc -> {
                    technologies.forEach(tech -> {
                        // create assignment
                        Map<String, Integer> assignment = new HashMap<>(orderedClassifications.size() + orderedTechnologies.size());
                        assignment.put("CPC", orderedClassifications.indexOf(cpc));
                        assignment.put("Technology", orderedTechnologies.indexOf(tech));
                        assignment.put("WIPO", wipoIdx);
                        assignments.add(assignment);

                    });
                });
            }
        });
        return assignments;
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        Map<String,Set<String>> patentToClassificationMap = Database.getPatentToClassificationMap();
        Map<String,Collection<String>> classificationToGatherPatentsMap = getClassificationToGatherPatentsMap(trainingData,patentToClassificationMap);
        Map<String,String> wipoMap = WIPOHelper.getWIPOMap();
        orderedTechnologies = new ArrayList<>(trainingData.keySet());
        orderedClassifications = new ArrayList<>(classificationToGatherPatentsMap.keySet());
        orderedWIPO = WIPOHelper.getOrderedClassifications();
        bayesianNet = new BayesianNet();

        List<Map<String,Integer>> assignments = getAssignments(trainingData,orderedTechnologies,patentToClassificationMap,orderedClassifications,wipoMap,orderedWIPO);

        // set data
        bayesianNet.setTrainingData(assignments);

        // add node
        Node cpcNode = bayesianNet.addNode("CPC",orderedClassifications.size());
        Node techNode = bayesianNet.addNode("Technology",orderedTechnologies.size());
        Node wipoNode = bayesianNet.addNode("WIPO",orderedWIPO.size());
        bayesianNet.connectNodes(cpcNode,techNode);
        bayesianNet.connectNodes(wipoNode,techNode);
        bayesianNet.addFactorNode(null,cpcNode);
        bayesianNet.addFactorNode(null, wipoNode);
        bayesianNet.addFactorNode(null, wipoNode,techNode,wipoNode);

        // learn
        bayesianNet.applyLearningAlgorithm(new BayesianLearningAlgorithm(bayesianNet,alpha),1);
        System.out.println("Num assignments: "+assignments.size());
    }

    private NaiveGatherClassifierWithWIPO(double alpha) {
        super(alpha);
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int limit) {
        Set<String> cpcClasses = new HashSet<>();
        portfolio.forEach(token->cpcClasses.addAll(Database.subClassificationsForPatent(token)));
        if(cpcClasses.isEmpty()) return Collections.emptyList();

        Set<String> wipoClasses = new HashSet<>();
        portfolio.forEach(token->{
            if(WIPOHelper.getWIPOMap().containsKey(token)) wipoClasses.add(WIPOHelper.getWIPOMap().get(token));
        });

        double[] observationCPC = getObservation(cpcClasses,orderedClassifications);
        double[] observationWIPO = getObservation(wipoClasses,orderedWIPO);

        if(observationCPC==null||observationWIPO==null) return Collections.emptyList();

        FactorNode cpcFactor = new FactorNode(observationCPC,new String[]{"CPC"},new int[]{orderedClassifications.size()});
        FactorNode wipoFactor = new FactorNode(observationWIPO,new String[]{"WIPO"},new int[]{orderedWIPO.size()});
        cpcFactor.reNormalize(new DivideByPartition());
        wipoFactor.reNormalize(new DivideByPartition());

        FactorNode condTechFactor = bayesianNet.findNode("Technology").getFactors().get(0);
        FactorNode result = cpcFactor.multiply(condTechFactor).multiply(wipoFactor).sumOut(new String[]{"CPC","WIPO"});
        result.reNormalize(new DivideByPartition());

        List<Pair<String,Double>> values = new ArrayList<>();
        double[] array = result.getWeights();
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

    public static void main(String[] args) {
        double alpha = 20d;
        NaiveGatherClassifierWithWIPO classifier = new NaiveGatherClassifierWithWIPO(alpha);
        classifier.train(SplitModelData.getBroadDataMap(SplitModelData.trainFile));
        // save
        classifier.save();

        classifier = get();
        System.out.println("Num tech: "+classifier.orderedTechnologies.size());

    }
}
