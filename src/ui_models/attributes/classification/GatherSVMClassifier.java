package ui_models.attributes.classification;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import lombok.Getter;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import svm.SVMHelper;
import svm.genetics.SVMSolution;
import svm.genetics.SVMSolutionCreator;
import svm.genetics.SVMSolutionListener;
import svm.libsvm.svm_model;
import svm.libsvm.svm_parameter;
import ui_models.portfolios.AbstractPortfolio;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/16/2017.
 */
public class GatherSVMClassifier implements ClassificationAttr {
    private static svm_model MODEL;
    private static List<String> ORDERED_TECHNOLOGIES;
    private static GatherSVMClassifier classifier;
    private static final int timeLimit = 10*60*1000;

    protected svm_model model;
    protected svm_parameter param;
    @Getter
    protected List<String> orderedTechnologies;
    private static final File techFile = new File("data/ordered_gather_technologies_svm.jobj");
    private static final File modelFile = new File("data/gather_svm_classification_model.jobj");

    // trainable version
    public GatherSVMClassifier(svm_parameter param) {
        this.param=param;
    }
    // pretrained model
    public GatherSVMClassifier(svm_model model, List<String> orderedTechnologies) {
        this.model=model;
        this.orderedTechnologies=orderedTechnologies;
        this.param=model.param;
    }

    @Override
    public void save() {
        // Save Model
        Database.trySaveObject(model,modelFile);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,techFile);
    }

    public static GatherSVMClassifier get() {
        if(classifier==null) {
            classifier=load();
        }
        return classifier;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        Map<String,INDArray> lookupTable = SimilarPatentFinder.getLookupTable();
        return portfolio.getTokens().stream().map(token->{
            INDArray vector = lookupTable.get(token);
            if(vector!=null) {
                double[] results = SVMHelper.svmPredictionDistribution(new double[][]{vector.data().asDouble()},model)[0];
                List<Pair<String,Double>> maxResults = new ArrayList<>();
                for(int i = 0; i < results.length; i++) {
                    maxResults.add(new Pair<>(orderedTechnologies.get(model.label[i]),results[i]));
                }
                return maxResults.stream().sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit).collect(Collectors.toList());
            } else return null;
        }).filter(d->d!=null).flatMap(list->list.stream()).collect(Collectors.groupingBy(p->p.getFirst(), Collectors.summingDouble(p->p.getSecond()))).entrySet().stream()
                .map(e->new Pair<>(e.getKey(),e.getValue().doubleValue())).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        System.out.println("Building svm data...");
        this.orderedTechnologies=new ArrayList<>(trainingData.keySet());
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(trainingData,this.orderedTechnologies);

        System.out.println("Training svm model...");
        model = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(trainingData,orderedTechnologies);

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new SVMSolutionCreator(training,validationData,orderedTechnologies);
        Listener listener = null;// new SVMSolutionListener();
        GeneticAlgorithm<SVMSolution> algorithm = new GeneticAlgorithm<>(creator,30,listener,20);
        algorithm.simulate(timeLimit,0.5,0.5);
        return new GatherSVMClassifier(algorithm.getBestSolution().getModel(),orderedTechnologies);
    }

    @Override
    public int numClassifications() {
        return orderedTechnologies.size();
    }

    @Override
    public Collection<String> getClassifications() {
        return new HashSet<>(orderedTechnologies);
    }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new GatherSVMClassifier(param);
    }

    public static GatherSVMClassifier load() {
        if(MODEL==null) {
            MODEL=(svm_model) Database.tryLoadObject(modelFile);
        }
        if(ORDERED_TECHNOLOGIES==null) {
            ORDERED_TECHNOLOGIES=(List<String>) Database.tryLoadObject(techFile);
        }

        return new GatherSVMClassifier(MODEL,ORDERED_TECHNOLOGIES);
    }

    public static void main(String[] args) {
        // build gather svm

        // svm parameters
        svm_parameter param = new svm_parameter();
        param.probability = 1;
        param.gamma = 0.01;
        param.C = 70d;
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.SIGMOID;
        param.cache_size = 20000;
        param.eps = 0.00001;
        param.shrinking=0;
        param.coef0=-0.3;
        param.p=0.5;


        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());

        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies);

        System.out.println("Training svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        // Save Model
        Database.trySaveObject(m,modelFile);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,techFile);

    }
}