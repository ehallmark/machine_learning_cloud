package seeding;

import analysis.SimilarPatentFinder;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.Emailer;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 9/5/16.
 */
public class UpdateDefaultCandidateSet {
    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Map<String,Pair<Float,INDArray>> vocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        new SimilarPatentFinder(vocab);
        File tmpFile = new File("temporary_vector_file.txt");
        if(tmpFile.exists())tmpFile.delete();
        new SimilarPatentFinder(Database.getValuablePatentsToList(),tmpFile,"**ALL**",vocab);
        System.out.println("Switching files...");
        File old = new File("old_vector_file.txt");
        if(old.exists())old.delete();
        new File(Constants.PATENT_VECTOR_LIST_FILE).renameTo(old);
        if(tmpFile.renameTo(new File(Constants.PATENT_VECTOR_LIST_FILE))) {
            new Emailer("Succesfully transformed default candidate set!");
            System.out.println("Sucessfully completed!!!");
        } else {
            new Emailer("Failed to copy candidate set file:( BAD!!!!");
            System.out.println("Failed to complete properly :(");
        }

    }
}
