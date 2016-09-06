package seeding;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.Emailer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 9/5/16.
 */
public class UpdateDefaultCandidateSet {
    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Map<String,Pair<Float,INDArray>> vocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        List<Patent> patentList = new SimilarPatentFinder(vocab).getPatentList();
        Map<String,INDArray> cache = SimilarPatentFinder.getGlobalCache();
        ResultSet rs = Database.getValuablePatents();
        AtomicInteger cntr = new AtomicInteger(0);
        final int chunkSize = 10000;
        List<String> current = new ArrayList<>(chunkSize);
        while(rs.next()) {
            String name = rs.getString(1);
            if(!cache.containsKey(name)) {
                current.add(name);
                if(current.size()>=chunkSize) {
                    ResultSet inner = Database.selectPatentVectors(current);
                    int offset = 2; // Due to the pub_doc_number field
                    while (inner.next()) {
                        System.out.println(cntr.getAndIncrement());
                        try {
                            INDArray array = SimilarPatentFinder.handleResultSet(inner, offset, vocab);
                            if (array != null) {
                                patentList.add(new Patent(inner.getString(1), array));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    current.clear();
                }
            } else {
                System.out.println(cntr.getAndIncrement());
            }
        }
        if(current.size()>0) {
            ResultSet inner = Database.selectPatentVectors(current);
            int offset = 2; // Due to the pub_doc_number field
            while (inner.next()) {
                try {
                    INDArray array = SimilarPatentFinder.handleResultSet(inner, offset, vocab);
                    if (array != null) {
                        patentList.add(new Patent(inner.getString(1), array));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            current.clear();
        }
        System.out.println("Switching files...");
        File old = new File("old_vector_file.txt");
        if(old.exists())old.delete();
        new File(Constants.PATENT_VECTOR_LIST_FILE).renameTo(old);
        // Serialize List
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PATENT_VECTOR_LIST_FILE))));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
            new Emailer("Succesfully transformed default candidate set!");
            System.out.println("Sucessfully completed!!!");
        } catch(Exception e) {
            new Emailer("Failed to copy candidate set file:( BAD!!!!");
            System.out.println("Failed to complete properly :(");
        }

    }
}
