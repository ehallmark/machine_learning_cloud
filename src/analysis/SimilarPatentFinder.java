package analysis;

import com.google.gson.Gson;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
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
import server.tools.AbstractPatent;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    private MinHeap<Patent> heap;
    private List<Patent> patentList;
    //private Map<String, String> assigneeMap;
    private Map<Patent.Type,Double> percentagesMap;

    public SimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE));
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile) throws SQLException, IOException, ClassNotFoundException{
        this(candidateSet, patentListFile, Constants.VECTOR_PERCENTAGES);
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, Map<Patent.Type, Double> percentagesMap) throws SQLException,IOException, ClassNotFoundException {
        // construct lis
        this.percentagesMap=percentagesMap;
        System.out.println("--- Started Loading Panasonic Patent Vector List ---");
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
                        patentList.add(new Patent(rs.getString(1), array, Patent.Type.ALL));
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
            ois.close();
        }
        System.out.println("--- Finished Loading Panasonic Patent Vector List ---");

    }

    private INDArray handleResultSet(ResultSet rs, int offset) throws SQLException{
        return handleResultSet(rs, offset, percentagesMap);
    }

    private INDArray handleResultSet(ResultSet rs, int offset, Map<Patent.Type, Double> percentagesMap) throws SQLException {
        INDArray array = null;
        double total = 0.0;
        for (int i = 0; i < Constants.VECTOR_TYPES.size() - 3; i++) {
            Array data = rs.getArray(i + offset);
            Patent.Type type = Constants.VECTOR_TYPES.get(i);
            if(data!=null && percentagesMap.get(type)>0.0) {
                if(array==null)array=Nd4j.zeros(Constants.VECTOR_LENGTH);
                Double multiple = percentagesMap.get(type);
                array.addi(Nd4j.create(VectorHelper.toPrim((Double[]) data.getArray())).mul(multiple));
                total+=multiple;
            }
        }

        // handle merging of class / subclass
        INDArray mergedClassVector = mergedClassVector(rs, offset);
        if (mergedClassVector != null && percentagesMap.get(Patent.Type.CLASS)>0.0) {
            if(array==null)array=Nd4j.zeros(Constants.VECTOR_LENGTH);
            Double multiple = percentagesMap.get(Patent.Type.CLASS);
            array.addi(mergedClassVector.mul(multiple));
            total+=multiple;
        }

        // handle claims index VECTOR_TYPES.size()-1
        Array data = rs.getArray(Constants.VECTOR_TYPES.size() + offset);
        if (data != null && percentagesMap.get(Patent.Type.CLAIM)>0.0) {
            Integer[] claimIndices = (Integer[]) data.getArray();
            Double[][] claims = (Double[][]) rs.getArray(offset + Constants.VECTOR_TYPES.size() - 1).getArray();
            assert claimIndices.length == claims.length;
            Double multiple = percentagesMap.get(Patent.Type.CLAIM)/claims.length;
            INDArray avgClaim = Nd4j.create(VectorHelper.toPrim(claims)).mean(0);
            if(array==null)array=Nd4j.zeros(Constants.VECTOR_LENGTH);
            array.addi(avgClaim.mul(multiple));
            total+=multiple;
        }
        if(array==null) return null;
        else {
            assert total > 0.0 : "Diving by Zero!!! BAD!!!";
            return array.div(total);
        }
    }

    private INDArray mergedClassVector(ResultSet rs, int offset) throws SQLException {
        Array classData = rs.getArray(offset+Constants.VECTOR_TYPES.size()-3);
        Array subClassData = rs.getArray(offset+Constants.VECTOR_TYPES.size()-2);
        if(classData!=null&&subClassData!=null) {
            return Nd4j.create(VectorHelper.toPrim((Double[])classData.getArray())).mul(percentagesMap.get(Patent.Type.CLASS)).add(Nd4j.create(VectorHelper.toPrim((Double[])subClassData.getArray())).mul(percentagesMap.get(Patent.Type.SUBCLASS)));
        } else if(classData!=null) {
            return Nd4j.create(VectorHelper.toPrim((Double[])classData.getArray()));
        } else if(subClassData!=null) {
            return Nd4j.create(VectorHelper.toPrim((Double[])subClassData.getArray()));
        } else {
            return null;
        }
    }


    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, int limit) {
        // Find the highest (pairwise) assets
        if(this.patentList.size() > other.patentList.size()) return other.similarFromCandidateSet(this, limit); // switch
        assert this.patentList.size() < other.patentList.size(); // we want to iterate through smaller one first
        List<PatentList> patentLists = new ArrayList<>();
        setupMinHeap(limit);
        patentList.forEach(patent->{
            synchronized (Patent.class) {
                Patent.setBaseVector(patent.getVector());
                Patent.setSortType(Patent.Type.ALL);
                String patentName = patent.getName().split("\\s+")[0];
                other.patentList.forEach(otherPatent -> {
                    if (!otherPatent.getName().startsWith(patentName)) {
                        otherPatent.calculateSimilarityToTarget();
                        otherPatent.setReferringName(patent.getName());
                        System.out.println(otherPatent.getName() + " -> " + patent.getName());
                        heap.add(otherPatent);
                    }
                });
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            resultList.add(0, Patent.abstractClone(p, p.getReferringName()));
        }
        PatentList results = new PatentList(resultList);
        patentLists.add(results);
        return patentLists;
    }


    // returns null if patentNumber not found
    // returns empty if no results found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, Map<Patent.Type,Double> percentagesMap, int limit) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }

        int offset = 1;
        INDArray avgVector = handleResultSet(rs, offset, percentagesMap);
        List<PatentList> patentLists = new ArrayList<>();
        patentLists.add(similarPatentsHelper(avgVector, patentNumber, Patent.Type.ALL, limit));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents for "+patentNumber+": "+time+" seconds");

        return patentLists;
    }

    private synchronized PatentList similarPatentsHelper(INDArray baseVector, String patentNumber, Patent.Type type, int limit) {
        Patent.setBaseVector(baseVector);
        Patent.setSortType(type);
        patentList.forEach(patent -> {
            if(!patent.getName().startsWith(patentNumber)){
                patent.calculateSimilarityToTarget();
                heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            //String assignee = assigneeMap.get(p.getName());
            //if(assignee==null)assignee="";
            resultList.add(0, Patent.abstractClone(p, null));
        }
        PatentList results = new PatentList(resultList);
        return results;
    }

    // unit test!
    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder();
            System.out.println("Searching ALL (STRICT) similar patents for 7056704");
            List<PatentList> list = finder.findSimilarPatentsTo("7056704",Constants.VECTOR_PERCENTAGES, 20);
            if(list!=null)list.forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
            System.out.println("Searching similar patent CLAIMS (NON STRICT) for 7056704");
            finder.findSimilarPatentsTo("7056704", Constants.VECTOR_PERCENTAGES, 20).forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
