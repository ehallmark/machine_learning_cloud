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

    public SimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE));
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile) throws SQLException,IOException, ClassNotFoundException {
        // construct list
        System.out.println("--- Started Loading Panasonic Patent Vector List ---");
        if(!patentListFile.exists()) {
            patentList = new LinkedList<>();
            ResultSet rs;
            if(candidateSet==null) rs = Database.selectAllPatentVectors();
            else rs = Database.selectPatentVectors(candidateSet);
            int count = 0;
            int offset = 2; // Due to the pub_doc_number field
            while (rs.next()) {
                String patentNumber = rs.getString(1);
                for(int i = 0; i < Constants.VECTOR_TYPES.size()-1; i++) {
                    Array data = rs.getArray(i+offset);
                    if(data!=null) {
                        Patent.Type type = Constants.VECTOR_TYPES.get(i);
                        patentList.add(new Patent(patentNumber+" "+type.toString().toLowerCase(), Nd4j.create(VectorHelper.toPrim((Double[])data.getArray())), type));
                    }
                }
                // handle claims index VECTOR_TYPES.size()-1
                if(rs.getArray(offset+Constants.VECTOR_TYPES.size())!=null) {
                    Integer[] claimIndices = (Integer[]) rs.getArray(offset + Constants.VECTOR_TYPES.size()).getArray();
                    Double[][] claims = (Double[][]) rs.getArray(offset + Constants.VECTOR_TYPES.size() - 1).getArray();
                    assert claimIndices.length == claims.length;
                    for (int i = 0; i < claimIndices.length; i++) {
                        assert (claims[i] != null);
                        Integer index = claimIndices[i];
                        patentList.add(new Patent(patentNumber + " claim " + index.toString(), Nd4j.create(VectorHelper.toPrim(claims[i])), Patent.Type.CLAIM));
                    }
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
        /*
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
        System.out.println("--- Finished Loading Assignee Map ---"); */
    }

    /*
    private void handlePatentsSoFar(List<String> patentsSoFar)throws SQLException{
        ResultSet rs = Database.selectAssignees(patentsSoFar);
        while(rs.next()) {
            assigneeMap.put(rs.getString(1),rs.getString(2));
        }
        rs.close();
    }*/

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    // returns null if patentNumber not found
    // returns empty if no results found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, Patent.Type type, int limit, boolean strictness) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }
        int colIndex = Constants.VECTOR_TYPES.indexOf(type);
        List<Integer> indices = new ArrayList<>();
        if(colIndex<0) {
            // do all
            for(int i = 0; i < Constants.VECTOR_TYPES.size(); i++) {
                indices.add(i);
            }
        } else {
            indices.add(colIndex);
        }
        int claimIndex = Constants.VECTOR_TYPES.indexOf(Patent.Type.CLAIM);

        int offset = 1;
        List<PatentList> patentLists = new ArrayList<>();
        for(int index : indices) {
            if (rs.getArray(index+offset) == null) continue;
            if(index==claimIndex) {
                Integer[] claimIndices = (Integer[])rs.getArray(index+offset+1).getArray();
                int i=0;
                for(Double[] vec : (Double[][]) rs.getArray(index+offset).getArray()) {
                    if(vec==null)continue;
                    INDArray baseVector = Nd4j.create(VectorHelper.toPrim(vec));
                    assert baseVector != null : "Base vector is null!";
                    Patent.Type cType;
                    if(!strictness) cType=Patent.Type.ALL;
                    else cType=Patent.Type.CLAIM;
                    patentLists.add(similarPatentsHelper(baseVector, patentNumber, cType, "claim "+claimIndices[i], limit));
                    i++;
                }
            } else {
                INDArray baseVector = Nd4j.create(VectorHelper.toPrim((Double[]) rs.getArray(index+offset).getArray()));
                assert baseVector != null : "Base vector is null!";
                Patent.Type t = Constants.VECTOR_TYPES.get(index);
                Patent.Type cType;
                if(!strictness) cType=Patent.Type.ALL;
                else cType=t;
                patentLists.add(similarPatentsHelper(baseVector, patentNumber, cType, t.toString().toLowerCase(), limit));
            }
        }

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents for "+patentNumber+": "+time+" seconds");

        return patentLists;
    }

    private synchronized PatentList similarPatentsHelper(INDArray baseVector, String patentNumber, Patent.Type type, String name, int limit) {
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
            resultList.add(0, Patent.abstractClone(p));
        }
        PatentList results = new PatentList(resultList, name);
        return results;
    }

    // unit test!
    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder();
            System.out.println("Searching ALL (STRICT) similar patents for 7056704");
            List<PatentList> list = finder.findSimilarPatentsTo("7056704",Patent.Type.ALL, 20, true);
            if(list!=null)list.forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
            System.out.println("Searching similar patent CLAIMS (NON STRICT) for 7056704");
            finder.findSimilarPatentsTo("7056704", Patent.Type.CLAIM, 20, false).forEach(p->{
                System.out.println(new Gson().toJson(p));
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
