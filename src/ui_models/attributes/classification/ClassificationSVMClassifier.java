package ui_models.attributes.classification;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import graphical_models.classification.CPCKMeans;
import lombok.Getter;
import model_testing.GatherTechnologyScorer;
import model_testing.ModelTesterMain;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import server.SimilarPatentServer;
import svm.SVMHelper;
import svm.genetics.CPCSVMSolutionCreator;
import svm.genetics.SVMSolution;
import svm.genetics.SVMSolutionCreator;
import svm.libsvm.svm_model;
import svm.libsvm.svm_parameter;
import ui_models.portfolios.AbstractPortfolio;
import util.MathHelper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/16/2017.
 */
public class ClassificationSVMClassifier extends GatherSVMClassifier {
    private static svm_model MODEL;
    private static List<String> ORDERED_TECHNOLOGIES;
    private static List<String> ORDERED_CLASSIFICATIONS;
    private static ClassificationSVMClassifier classifier;
    private static final int timeLimit = 10*60*1000;

    private static final File techFile = new File("data/ordered_gather_technologies_cpc_svm.jobj");
    private static final File classFile = new File("data/ordered_gather_classifications_cpc_svm.jobj");
    private static final File modelFile = new File("data/gather_cpc_svm_classification_model.jobj");

    @Getter
    protected List<String> orderedClassifications;
    protected int cpcDepth;

    // trainable version
    public ClassificationSVMClassifier(svm_parameter param, int cpcDepth) {
        super(param);
    }
    // pretrained model
    public ClassificationSVMClassifier(svm_model model, List<String> orderedTechnologies, List<String> orderedClassifications, int cpcDepth) {
        super(model,orderedTechnologies);
        this.orderedClassifications=orderedClassifications;
    }

    @Override
    public void save() {
        // Save Model
        Database.trySaveObject(model,modelFile);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,techFile);

        Database.trySaveObject(orderedClassifications,classFile);
    }

    public static ClassificationSVMClassifier get() {
        if(classifier==null) {
            classifier=load();
        }
        return classifier;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        return portfolio.getTokens().stream().map(token->{
            double[] vector = CPCKMeans.classVectorForPatents(Arrays.asList(token),orderedClassifications,limit);
            if(vector!=null && Arrays.stream(vector).sum()>0d) {
                double[] results = SVMHelper.svmPredictionDistribution(new double[][]{vector},model)[0];
                List<Pair<String,Double>> maxResults = new ArrayList<>();
                for(int i = 0; i < results.length; i++) {
                    int labelIdx = model.label[i];
                    if(labelIdx>=0) {
                        maxResults.add(new Pair<>(orderedTechnologies.get(labelIdx), results[i]));
                    }
                }
                if(maxResults.isEmpty()) return null;
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
        // get patents
        List<String> patents = trainingData.entrySet().stream().flatMap(e->e.getValue().stream()).distinct().filter(p->Database.classificationsFor(p).size()>0).collect(Collectors.toList());
        // get classifications
        this.orderedClassifications = patents.stream().flatMap(p-> Database.classificationsFor(p).stream()).distinct().collect(Collectors.toList());

        Pair<double[][],double[][]> training = SVMHelper.mapToCPCSVMData(trainingData,this.orderedTechnologies,orderedClassifications,cpcDepth);

        System.out.println("Training svm model...");
        model = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToCPCSVMData(trainingData,orderedTechnologies,orderedClassifications,cpcDepth);

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new CPCSVMSolutionCreator(training,validationData,orderedTechnologies,orderedClassifications,cpcDepth);
        Listener listener = null;// new SVMSolutionListener();
        GeneticAlgorithm<SVMSolution> algorithm = new GeneticAlgorithm<>(creator,30,listener,20);
        algorithm.simulate(timeLimit,0.5,0.5);
        return new ClassificationSVMClassifier(algorithm.getBestSolution().getModel(),orderedTechnologies,orderedClassifications,cpcDepth);
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
        return new ClassificationSVMClassifier(param,cpcDepth);
    }

    public static ClassificationSVMClassifier load() {
        if(MODEL==null) {
            MODEL=(svm_model) Database.tryLoadObject(modelFile);
        }
        if(ORDERED_TECHNOLOGIES==null) {
            ORDERED_TECHNOLOGIES=(List<String>) Database.tryLoadObject(techFile);
        }
        if(ORDERED_CLASSIFICATIONS==null) {
            ORDERED_CLASSIFICATIONS=(List<String>) Database.tryLoadObject(classFile);
        }

        return new ClassificationSVMClassifier(MODEL,ORDERED_TECHNOLOGIES,ORDERED_CLASSIFICATIONS,CPCKMeans.DEFAULT_CPC_DEPTH);
    }

    public static void main(String[] args) {
        Database.initializeDatabase();
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

        int cpcDepth = CPCKMeans.DEFAULT_CPC_DEPTH;

        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());

        // get patents
        List<String> patents = gatherTrainingMap.entrySet().stream().flatMap(e->e.getValue().stream()).distinct().filter(p->Database.classificationsFor(p).size()>0).collect(Collectors.toList());

        // get classifications
        List<String> classifications = CPCKMeans.getClassifications(patents,cpcDepth);

        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToCPCSVMData(gatherTrainingMap,orderedTechnologies,classifications,cpcDepth);

        System.out.println("Training svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        // Save Model
        Database.trySaveObject(m,modelFile);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,techFile);

        Database.trySaveObject(classifications, classFile);

        ModelTesterMain.testModel("SVM CPC Model [n=3] ",new GatherTechnologyScorer(new ClassificationSVMClassifier(m,orderedTechnologies,classifications,cpcDepth)),SplitModelData.getBroadDataMap(SplitModelData.testFile),3);
    }
}