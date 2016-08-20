package analysis;

import com.google.gson.Gson;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;
import tools.PatentList;
import tools.VectorHelper;

import java.io.*;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import server.tools.AbstractPatent;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<Patent> patentList;
    protected static INDArray eigenVectors;
    static{
        try {
            eigenVectors=PCAModel.loadAndReturnEigenVectors();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    //private Map<String, String> assigneeMap;

    public SimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE));
    }


    public SimilarPatentFinder(List<String> candidateSet, File patentListFile) throws SQLException,IOException, ClassNotFoundException {
        // construct lis
        System.out.println("--- Started Loading Patent Vectors ---");
        if (!patentListFile.exists()) {
            ResultSet rs;
            if (candidateSet == null) {
                candidateSet = Database.getValuablePatentsToList();
            }
            int arrayCapacity = candidateSet.size();
            patentList = new ArrayList<>(arrayCapacity);
            rs = Database.selectPatentVectors(candidateSet);
            int count = 0;
            int offset = 2; // Due to the pub_doc_number field
            while (rs.next()) {
                try {
                    INDArray array = handleResultSet(rs, offset);
                    if(array!=null) {
                        patentList.add(new Patent(rs.getString(1), array));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
            // PCA
            if(eigenVectors!=null) patentList.forEach(p->p.setVector(p.getVector().mmuli(eigenVectors)));
            ois.close();
        }
        System.out.println("--- Finished Loading Patent Vectors ---");

    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    private static INDArray handleResultSet(ResultSet rs, int offset) throws SQLException {
        INDArray array = null;
        Array sqlArray = rs.getArray(offset);
        if(sqlArray!=null) array=Nd4j.create(VectorHelper.toPrim((Double[])sqlArray.getArray()));
        return array;
    }



    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit) throws SQLException {
        // Find the highest (pairwise) assets
        List<PatentList> patentLists = new ArrayList<>();
        Set<String> patentNames = other.patentList.stream().map(p->p.getName()).collect(Collectors.toSet());
        setupMinHeap(limit);
        other.patentList.forEach(patent->{
            try {
                patentLists.add(findSimilarPatentsTo(patent.getName().split("\\s+")[0], patent.getVector(), patentNames, threshold, limit).get(0));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        mergePatentLists(patentLists, limit);
        return patentLists;
    }

    private static void mergePatentLists(List<PatentList> patentLists, int limit) {
        PriorityQueue<AbstractPatent> queue = new PriorityQueue<>();
        for(PatentList list: patentLists) {
            queue.addAll(list.getPatents());
        }
        patentLists.clear();
        patentLists.add(new PatentList(new ArrayList<>(queue).subList(Math.max(0,queue.size()-limit-1), queue.size()-1)));
    }



    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        long startTime = System.currentTimeMillis();
        if(patentNamesToExclude ==null) {
            patentNamesToExclude=new HashSet<>();
            patentNamesToExclude.add(patentNumber);
        }
        setupMinHeap(limit);

        List<PatentList> patentLists = new ArrayList<>();
        patentLists.add(similarPatentsHelper(avgVector, patentNumber, patentNamesToExclude, threshold, limit));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents for "+patentNumber+": "+time+" seconds");

        return patentLists;
    }

    public Double angleBetweenPatents(String name1, String name2) throws SQLException {
        INDArray first = getVectorFromDB(name1);
        INDArray second = getVectorFromDB(name2);
        if(first!=null && second!=null) {
            //valid
            return Transforms.cosineSim(first,second);
        } else return null;
    }

    // returns null if patentNumber not found
    public List<PatentList> findOppositePatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        List<PatentList> toReturn = findSimilarPatentsTo(patentNumber, avgVector.mul(-1.0), patentNamesToExclude, threshold, limit);
        for(PatentList l : toReturn) {
            l.getPatents().forEach(p->p.flipSimilarity());
        }
        return toReturn;
    }

    // returns empty if no results found
    public List<PatentList> findOppositePatentsTo(String patentNumber, double threshold, int limit) throws SQLException {
        return findOppositePatentsTo(patentNumber, getVectorFromDB(patentNumber), null, threshold, limit);
    }


    // returns empty if no results found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, double threshold, int limit) throws SQLException {
        return findSimilarPatentsTo(patentNumber, getVectorFromDB(patentNumber), null, threshold, limit);
    }

    private INDArray getVectorFromDB(String patentNumber) throws SQLException {
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }
        int offset = 1;
        INDArray avgVector = handleResultSet(rs, offset);
        if(eigenVectors!=null) avgVector.mmuli(eigenVectors);
        return avgVector;
    }

    private synchronized PatentList similarPatentsHelper(INDArray baseVector, String patentNumber, Set<String> patentNamesToExclude, double threshold, int limit) {
        Patent.setBaseVector(baseVector);
        patentList.forEach(patent -> {
            if(!patentNamesToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget();
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            //String assignee = assigneeMap.get(p.getName());
            //if(assignee==null)assignee="";
            resultList.add(0, Patent.abstractClone(p, patentNumber));
        }
        PatentList results = new PatentList(resultList);
        return results;
    }

    // unit test!
    public static void main(String[] args) throws Exception {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder();
            System.out.println("Most similar: ");
            PatentList list = finder.findSimilarPatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
            System.out.println("Most opposite: ");
            list = finder.findOppositePatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
