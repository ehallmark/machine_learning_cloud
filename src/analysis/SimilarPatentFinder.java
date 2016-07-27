package analysis;

import com.google.gson.Gson;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;
import tools.VectorHelper;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    private MinHeap<Patent> heap;
    private List<Patent> patentList;
    private int num1DVectors = Constants.DEFAULT_1D_VECTORS.size();
    private int num2DVectors = Constants.DEFAULT_2D_VECTORS.size();
    private Map<String, String> assigneeMap;

    public SimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        this(new File(Constants.PATENT_VECTOR_LIST_FILE), new File(Constants.ASSIGNEE_MAP_FILE));
    }

    public SimilarPatentFinder(File patentListFile, File assigneeMapFile) throws SQLException,IOException, ClassNotFoundException {
        // construct list
        System.out.println("--- Started Loading Patent Vector List ---");
        if(!patentListFile.exists()) {
            patentList = new LinkedList<>();
            ResultSet rs = Database.selectPatentVectors();
            int count = 0;
            int offset = 1; // Due to the pub_doc_number field
            while (rs.next()) {
                patentList.add(new Patent(rs.getString(1), VectorHelper.extractResultSetToVector(rs, num1DVectors, num2DVectors, offset)));
                System.out.println(++count);
            }
            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            patentList = (List<Patent>) ois.readObject();
            ois.close();
        }
        System.out.println("--- Finished Loading Patent Vector List ---");
        System.out.println("--- Started Loading Assignee Map ---");
        if(!assigneeMapFile.exists()) {
            // Construct assignee map
            assigneeMap = new HashMap<>();
            int chunkSize = 10;
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicInteger total = new AtomicInteger(0);
            List<String> patentsSoFar = new ArrayList<>();
            for(Patent p : patentList) {
                patentsSoFar.add(p.getName());
                if(cnt.getAndIncrement() >= chunkSize) {
                    handlePatentsSoFar(patentsSoFar);
                    patentsSoFar.clear();
                    cnt.set(0);
                }
                System.out.println(total.getAndIncrement());
            }
            // get remaining
            if(!patentsSoFar.isEmpty()) {
                handlePatentsSoFar(patentsSoFar);
            }

            // Serialize Map to file
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(assigneeMapFile)));
            oos.writeObject(assigneeMap);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(assigneeMapFile)));
            assigneeMap = (Map<String,String>) ois.readObject();
            ois.close();
        }
        System.out.println("--- Finished Loading Assignee Map ---");
    }

    private void handlePatentsSoFar(List<String> patentsSoFar)throws SQLException{
        ResultSet rs = Database.selectAssignees(patentsSoFar);
        while(rs.next()) {
            assigneeMap.put(rs.getString(1),rs.getString(2));
        }
        rs.close();
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    // returns null if patentNumber not found
    // returns empty if no results found
    public List<AbstractPatent> findSimilarPatentsTo(String patentNumber, int limit) throws SQLException {
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }
        INDArray baseVector = VectorHelper.extractResultSetToVector(rs, num1DVectors, num2DVectors);
        synchronized(Patent.class) {
            Patent.setBaseVector(baseVector);
            patentList.forEach(patent -> {if(!patent.getName().equals(patentNumber))patent.calculateSimilarityToTarget(); heap.add(patent);});
            List<AbstractPatent> results = new ArrayList<>(limit);
            while (!heap.isEmpty()) {
                Patent p = heap.remove();
                String assignee = assigneeMap.get(p.getName());
                if(assignee==null)assignee="";
                results.add(0, Patent.abstractClone(p, assignee));
            }
            long endTime = System.currentTimeMillis();
            double time = new Double(endTime-startTime)/1000;
            System.out.println("Time to find similar patents for "+patentNumber+": "+time+" seconds");
            return results;
        }
    }

    // unit test!
    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(new File(Constants.PATENT_VECTOR_LIST_FILE), new File(Constants.ASSIGNEE_MAP_FILE));
            System.out.println("Searching similar patents for 7056704");
            finder.findSimilarPatentsTo("7056704", 20).forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
            System.out.println("Searching similar patents for 8481929");
            finder.findSimilarPatentsTo("8481929", 20).forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
