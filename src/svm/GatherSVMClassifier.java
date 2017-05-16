package svm;

import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import server.SimilarPatentServer;
import svm.libsvm.svm_model;
import svm.libsvm.svm_parameter;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.AbstractPortfolio;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/16/2017.
 */
public class GatherSVMClassifier extends ClassificationAttr {
    protected static svm_model MODEL;
    protected static List<String> ORDERED_TECHNOLOGIES;

    protected svm_model model;
    protected List<String> orderedTechnologies;
    private static final File techFile = new File("data/ordered_gather_technologies_svm.jobj");
    private static final File modelFile = new File("data/gather_svm_classification_model.jobj");

    public GatherSVMClassifier(svm_model model, List<String> orderedTechnologies) {
        this.model=model;
        this.orderedTechnologies=orderedTechnologies;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        return portfolio.getTokens().stream().map(token->{
            INDArray vector = SimilarPatentServer.getLookupTable().vector(token);
            if(vector!=null) {
                double[] results = SVMHelper.svmPredict(new double[][]{vector.data().asDouble()},model);
                return orderedTechnologies.get((int) results[0]);
            } else return null;
        }).filter(d->d!=null).collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .map(e->new Pair<>(e.getKey(),e.getValue().doubleValue())).collect(Collectors.toList());
    }

    @Override
    public int numClassifications() {
        return orderedTechnologies.size();
    }

    @Override
    public Collection<String> getClassifications() {
        return new HashSet<>(orderedTechnologies);
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
        param.gamma = 0.5;
        param.nu = 0.5;
        param.C = 100;
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.SIGMOID;
        param.cache_size = 20000;
        param.eps = 0.001;


        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getGatherTechnologyTrainingDataMap();
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());

        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies);

        System.out.println("Training svm model...");
        svm_model m = SVMHelper.svmTrain(training.getFirst(),training.getSecond(),param);

        System.out.println("Testing svm model...");
        Pair<double[][],double[][]> testing = SVMHelper.mapToSVMData(SplitModelData.getGatherTechnologyTestDataMap(),orderedTechnologies);
        double[] ypred = SVMHelper.svmPredict(testing.getFirst(), m);
        for (int i = 0; i < testing.getFirst().length; i++){
            System.out.println("(Actual:" + orderedTechnologies.get((int) testing.getSecond()[i][0]) + " Prediction:" + orderedTechnologies.get((int) ypred[i]) + ")");
        }

        // Save Model
        Database.trySaveObject(m,modelFile);

        // Save Technology Ordering
        Database.trySaveObject(orderedTechnologies,techFile);
    }
}
