package models.classification_models;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateClassificationModels {
    public static void main(String[] args) throws Exception {
        NaiveGatherClassifier.main(args);
        GatherSVMClassifier.main(args);
    }
}
