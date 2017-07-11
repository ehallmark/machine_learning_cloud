package models.classification_models;

import models.genetics.GeneticAlgorithm;
import models.genetics.Listener;
import models.genetics.SolutionCreator;
import j2html.tags.Tag;
import lombok.Getter;
import models.model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.class_vectors.CPCSimilarityFinder;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.svm.SVMHelper;
import models.svm.genetics.SVMSolution;
import models.svm.genetics.SVMSolutionCreator;
import models.svm.libsvm.svm_model;
import models.svm.libsvm.svm_parameter;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/16/2017.
 */
public class GatherSVMClassifier implements ClassificationAttr {
    private static svm_model CPC_MODEL;
    private static svm_model PARAGRAPH_MODEL;
    private static List<String> ORDERED_TECHNOLOGIES;
    private static GatherSVMClassifier CPC_CLASSIFIER;
    private static GatherSVMClassifier PARAGRAPH_VECTOR_CLASSIFIER;
    private static final File TECH_FILE = new File("data/ordered_gather_technologies_svm.jobj");
    private static final File CPC_MODEL_FILE = new File("data/gather_svm_classification_model.jobj");
    private static final File PARAGRAPH_VECTOR_MODEL_FILE = new File("data/gather_svm_paragraph_vector_model.jobj");

    private static final int timeLimit = 10*60*1000;

    protected File modelFile;
    protected svm_model model;
    protected svm_parameter param;
    @Getter
    protected Map<String,INDArray> lookupTable;
    @Getter
    protected List<String> orderedTechnologies;

    public String getName() {
        return "SVM Classifier";
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    // trainable version
    public GatherSVMClassifier(svm_parameter param, File modelFile, Map<String,INDArray> lookupTable) {
        this.modelFile=modelFile;
        this.param=param;
        this.lookupTable=lookupTable;
    }
    // pretrained model
    public GatherSVMClassifier(svm_model model, List<String> orderedTechnologies, File modelFile, Map<String,INDArray> lookupTable) {
        this.model=model;
        this.lookupTable=lookupTable;
        this.orderedTechnologies=orderedTechnologies;
        this.param=model.param;
        this.modelFile=modelFile;
    }

    @Override
    public void save() {
        // Save Model
        Database.trySaveObject(model,modelFile);
    }

    public static GatherSVMClassifier getCPCModel() {
        if(CPC_CLASSIFIER==null) {
            CPC_CLASSIFIER=loadCPCModel();
        }
        return CPC_CLASSIFIER;
    }

    public static GatherSVMClassifier getParagraphVectorModel() {
        if(PARAGRAPH_VECTOR_CLASSIFIER==null) {
            PARAGRAPH_VECTOR_CLASSIFIER=loadParagraphVectorModel();
        }
        return PARAGRAPH_VECTOR_CLASSIFIER;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int limit) {
        return portfolio.stream().map(token->{
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
        System.out.println("Building models.svm data...");
        this.orderedTechnologies=new ArrayList<>(trainingData.keySet());
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(trainingData,this.orderedTechnologies, this.lookupTable);

        System.out.println("Training models.svm model...");
        model = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        System.out.println("Building models.svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(trainingData,orderedTechnologies,this.lookupTable);

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new SVMSolutionCreator(training,validationData,orderedTechnologies, lookupTable);
        Listener listener = null;// new SVMSolutionListener();
        GeneticAlgorithm<SVMSolution> algorithm = new GeneticAlgorithm<>(creator,30,listener,20);
        algorithm.simulate(timeLimit,0.5,0.5);
        return new GatherSVMClassifier(algorithm.getBestSolution().getModel(),orderedTechnologies,modelFile,lookupTable);
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
        return new GatherSVMClassifier(this.param,this.modelFile,lookupTable);
    }

    public static GatherSVMClassifier loadCPCModel() {
        if(CPC_MODEL==null) {
            CPC_MODEL=(svm_model) Database.tryLoadObject(CPC_MODEL_FILE);
        }
        if(ORDERED_TECHNOLOGIES==null) {
            ORDERED_TECHNOLOGIES=(List<String>) Database.tryLoadObject(TECH_FILE);
        }

        return new GatherSVMClassifier(CPC_MODEL,ORDERED_TECHNOLOGIES,CPC_MODEL_FILE,CPCSimilarityFinder.getLookupTable());
    }

    public static GatherSVMClassifier loadParagraphVectorModel() {
        if(PARAGRAPH_MODEL==null) {
            PARAGRAPH_MODEL=(svm_model) Database.tryLoadObject(PARAGRAPH_VECTOR_MODEL_FILE);
        }
        if(ORDERED_TECHNOLOGIES==null) {
            ORDERED_TECHNOLOGIES=(List<String>) Database.tryLoadObject(TECH_FILE);
        }

        return new GatherSVMClassifier(PARAGRAPH_MODEL,ORDERED_TECHNOLOGIES,PARAGRAPH_VECTOR_MODEL_FILE,SimilarPatentFinder.getLookupTable());
    }

    public static void trainParagraphVectorModel(Map<String,Collection<String>> gatherTrainingMap, List<String> orderedTechnologies) {
        // build gather models.svm

        // models.svm parameters
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


        System.out.println("Building models.svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies,SimilarPatentFinder.getLookupTable());

        System.out.println("Training models.svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        // Save Model
        Database.trySaveObject(m,PARAGRAPH_VECTOR_MODEL_FILE);
    }

    public static void trainCPCVectorModel(Map<String,Collection<String>> gatherTrainingMap, List<String> orderedTechnologies) {
        // build gather models.svm

        // models.svm parameters
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

        System.out.println("Building models.svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies, CPCSimilarityFinder.getLookupTable());

        System.out.println("Training models.svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        // Save Model
        Database.trySaveObject(m,CPC_MODEL_FILE);

    }

    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());

        System.out.println("Starting cpc model");
        trainCPCVectorModel(gatherTrainingMap,orderedTechnologies);

        System.out.println("Starting paragraph model");
        trainParagraphVectorModel(gatherTrainingMap, orderedTechnologies);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,TECH_FILE);

    }
}