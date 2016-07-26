package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;
import tools.VectorHelper;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    private MinHeap<Patent> heap;
    private List<Patent> patentList;
    public SimilarPatentFinder(File patentListFile) throws SQLException,IOException, ClassNotFoundException {
        // construct list
        if(!patentListFile.exists()) {
            patentList = new LinkedList<>();
            ResultSet rs = Database.selectPatentVectors();
            int count = 0;
            while (rs.next()) {
                INDArray vector = Nd4j.create(VectorHelper.toPrim((Double[]) rs.getArray(2).getArray()));
                patentList.add(new Patent(rs.getString(1), vector));
                System.out.println(++count);
            }
            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
        } else {
            // read from list
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            patentList = (List<Patent>) ois.readObject();
            ois.close();
        }
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    // returns null if patentNumber not found
    // returns empty if no results found
    public List<Patent> findSimilarPatentsTo(String patentNumber, int limit) throws SQLException {
        setupMinHeap(limit);
        int num1DVectors = 3;
        int num2DVectors = 2;
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }
        INDArray baseVector = VectorHelper.extractResultSetToVector(rs, num1DVectors, num2DVectors);
        synchronized(Patent.class) {
            Patent.setBaseVector(baseVector);
            patentList.parallelStream().forEach(patent -> heap.add(patent));
            List<Patent> results = new ArrayList<>(limit);
            while (!heap.isEmpty()) {
                Patent p = heap.remove();
                p.calculateSimilarityToTarget();
                results.add(0, heap.remove());
            }
            return results;
        }
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(new File(Constants.PATENT_VECTOR_LIST_FILE));
            finder.findSimilarPatentsTo("7056704", 20).forEach(p->{
                System.out.println(p.getName()+": "+p.getSimilarityToTarget());
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
