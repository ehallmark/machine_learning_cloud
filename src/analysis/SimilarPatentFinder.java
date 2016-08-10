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
            patentList = new LinkedList<>();
            ResultSet rs;
            if (candidateSet == null) rs = Database.selectAllPatentVectors();
            else rs = Database.selectPatentVectors(candidateSet);
            int count = 0;
            int offset = 2; // Due to the pub_doc_number field
            while (rs.next()) {
                try {
                    handleResultSet(rs, offset);
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

    private void handleResultSet(ResultSet rs, int offset) throws SQLException {
        INDArray array = null;
        double total = 0.0;
        String patentNumber = rs.getString(1);
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
        INDArray mergedClassVector = mergedClassVector(rs, patentNumber, offset);
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

        if(array!=null) {
            assert total > 0.0 : "Diving by Zero!!! BAD!!!";
            patentList.add(new Patent(patentNumber, array.div(total), Patent.Type.ALL));
        }
    }

    private INDArray mergedClassVector(ResultSet rs, String patentNumber, int offset) throws SQLException {
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

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, Patent.Type type, int limit, boolean strictness) {
        // Find the highest (pairwise) assets
        int colIndex = Constants.VECTOR_TYPES.indexOf(type);
        List<Integer> indices = new ArrayList<>();
        if(colIndex<0) {
            // do all
            int subclassIndex = Constants.VECTOR_TYPES.indexOf(Patent.Type.SUBCLASS);
            for(int i = 0; i < Constants.VECTOR_TYPES.size(); i++) {
                if(i!=subclassIndex)indices.add(i);
            }
        } else {
            indices.add(colIndex);
        }

        List<PatentList> patentLists = new ArrayList<>();
        for(int index : indices) {
            Patent.Type cType = Constants.VECTOR_TYPES.get(index);
            setupMinHeap(limit);
            addToHeap(other, cType, strictness);
            patentLists.add(extractResultsFromHeap(limit, cType));
        }
        return patentLists;
    }

    private synchronized void addToHeap(SimilarPatentFinder other, Patent.Type type, boolean strictness) {
        Patent.Type cType;
        if(!strictness)cType= Patent.Type.ALL;
        else cType = type;
        other.patentList.forEach(otherPatent->{
            Patent.setBaseVector(otherPatent.getVector());
            Patent.setSortType(cType);
            patentList.forEach(patent -> {
                if(!patent.getName().startsWith(otherPatent.getName().split("\\s+")[0])){
                    patent.calculateSimilarityToTarget();
                    patent.setReferringName(otherPatent.getName());
                    System.out.println(patent.getName()+ " -> "+otherPatent.getName());
                    heap.add(patent);
                }
            });

        });
    }

    private PatentList extractResultsFromHeap(int limit, Patent.Type type) {
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            resultList.add(0, Patent.abstractClone(p, p.getReferringName()));
        }
        PatentList results = new PatentList(resultList, type.toString().toLowerCase());
        return results;
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
            int subclassIndex = Constants.VECTOR_TYPES.indexOf(Patent.Type.SUBCLASS);
            for(int i = 0; i < Constants.VECTOR_TYPES.size(); i++) {
                if(i!=subclassIndex)indices.add(i);
            }
        } else {
            indices.add(colIndex);
        }
        int claimIndex = Constants.VECTOR_TYPES.indexOf(Patent.Type.CLAIM);
        int classIndex = Constants.VECTOR_TYPES.indexOf(Patent.Type.CLASS);

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
            } else if(index==classIndex) {
                Patent.Type cType;
                if(!strictness) cType=Patent.Type.ALL;
                else cType=Patent.Type.CLASS;
                patentLists.add(similarPatentsHelper(mergedClassVector(rs, patentNumber, offset), patentNumber, cType, "class", limit));

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
            resultList.add(0, Patent.abstractClone(p, null));
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
