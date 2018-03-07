package models.classification_models.svm.genetics;

import models.classification_models.GatherSVMClassifier;

/**
 * Created by Evan on 6/8/2017.
 */
public class OptimizeCPCModel {
    public static void main(String[] args) {
        OptimizeModelsHelper.optimizeModel(GatherSVMClassifier.getCPCModel());
    }
}
