package models.svm.genetics;

import models.classification_models.GatherSVMClassifier;

/**
 * Created by Evan on 6/8/2017.
 */
public class OptimizeParagraphVectorModel {
    public static void main(String[] args) {
        OptimizeModelsHelper.optimizeModel(GatherSVMClassifier.getParagraphVectorModel());
    }
}
