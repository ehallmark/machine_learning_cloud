package svm.genetics;

import ui_models.attributes.classification.GatherSVMClassifier;

/**
 * Created by Evan on 6/8/2017.
 */
public class OptimizeCPCModel {
    public static void main(String[] args) {
        OptimizeModelsHelper.optimizeModel(GatherSVMClassifier.getCPCModel());
    }
}
