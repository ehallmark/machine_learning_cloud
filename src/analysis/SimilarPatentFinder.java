package analysis;

import org.deeplearning4j.plot.BarnesHutTsne;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import server.tools.AbstractPatent;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<List<Patent>> patentLists;
    protected List<String> names;
    protected String name;

    //private Map<String, String> assigneeMap;

    public SimilarPatentFinder() throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE),"**ALL**");
    }

    public SimilarPatentFinder(List<List<String>> candidateSets, File patentListFile, List<String> names, String name) throws SQLException,IOException,ClassNotFoundException {
        this(candidateSets,patentListFile,names,null,name);
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name) throws SQLException,IOException,ClassNotFoundException {
        this(candidateSet!=null?Arrays.asList(candidateSet):null,patentListFile,Arrays.asList(name),null,name);
    }

    public SimilarPatentFinder(List<String> names, String name) throws SQLException {
        this.patentLists=new ArrayList<>(1);
        this.patentLists.add(new ArrayList<>(1));
        this.names=names;
        this.name=name;
        patentLists.addAll(names.stream().map(n->{try{return Arrays.asList(new Patent(n,getVectorFromDB(n)));}catch(Exception e) {e.printStackTrace(); return null; }}).filter(p->p!=null).collect(Collectors.toList()));
    }

    public SimilarPatentFinder(String name) throws  SQLException {
        this(Arrays.asList(name),name);
    }

    public SimilarPatentFinder(List<List<String>> candidateSets, File patentListFile, List<String> names, INDArray eigenVectors, String name) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        this.names=names;
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        AtomicInteger i = new AtomicInteger(0);
        if (!patentListFile.exists()) {
            assert(candidateSets==null||candidateSets.size()==names.size()) : "INVALID CANDIDATE SETS AND NAMES DIMENSIONS !!";
            this.patentLists=new ArrayList<>();
            ResultSet rs;
            int idx = i.getAndIncrement();
            List<String> candidateSet = candidateSets==null?null:candidateSets.get(idx);
            if (candidateSet == null) {
                candidateSet = Database.getValuablePatentsToList();
            }
            int arrayCapacity = candidateSet.size();
            List<Patent> patentList = new ArrayList<>(arrayCapacity);
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
            patentLists.add(patentList);
            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentLists);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            patentLists = ((List<List<Patent>>) ois.readObject());
            // PCA
            if(eigenVectors!=null) patentLists.forEach(patentList->patentList.forEach(p->p.getVector().mmuli(eigenVectors)));
            ois.close();
        }
        System.out.println("--- Finished Loading Patent Vectors ---");

    }

    public List<List<Patent>> getPatentLists() {
        return patentLists;
    }

    private static INDArray handleResultSet(ResultSet rs, int offset) throws SQLException {
        INDArray array = null;
        Array descArray = rs.getArray(offset);
        Array claimsArray = rs.getArray(offset+1);
        if(descArray!=null && claimsArray!=null) {
            array=Nd4j.create(VectorHelper.toPrim((Double[])descArray.getArray()));
            array.addi(Nd4j.create(VectorHelper.toPrim((Double[][])claimsArray.getArray())).mean(0));
            array.muli(0.5);
        } else if (descArray!=null) {
            array = Nd4j.create(VectorHelper.toPrim((Double[])descArray.getArray()));
        } else if(claimsArray!=null) {
            array = Nd4j.create(VectorHelper.toPrim((Double[][])claimsArray.getArray())).mean(0);
        }
        return array;
    }

    public String getName() {
        return name;
    }
    public List<String> getNames() {return names;}

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public static INDArray computeAvg(List<Patent> patentList) {
        INDArray thisAvg = Nd4j.create(patentList.size(),Constants.VECTOR_LENGTH);
        for(int i = 0; i < patentList.size(); i++) {
            thisAvg.putRow(i, patentList.get(i).getVector());
        }
        return thisAvg.mean(0);
    }

    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, boolean findDissimilar) throws SQLException {
        List<PatentList> patentLists = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                patentLists.addAll(similarFromCandidateSet(other, threshold, limit, findDissimilar));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        return patentLists;
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentLists()==null||other.getPatentLists().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        AtomicInteger i = new AtomicInteger(0);
        other.names.forEach(name->{
            int idx = i.getAndIncrement();
            INDArray otherAvg = computeAvg(other.getPatentLists().get(idx));
            List<Patent> patentList = other.patentLists.get(idx);
            Set<String> dontMatch = name.equals(name) ? null : patentList.stream().map(p->p.getName()).collect(Collectors.toSet());
            try {
                if(findDissimilar) lists.addAll(findOppositePatentsTo(other.getName(), otherAvg, dontMatch, threshold, limit));
                else lists.addAll(findSimilarPatentsTo(other.getName(), otherAvg, dontMatch, threshold, limit));

            } catch(SQLException sql) {
            }
        });
        return lists;
    }

    /*private static void mergePatentLists(List<PatentList> patentLists, int limit) {
        PriorityQueue<AbstractPatent> queue = new PriorityQueue<>();
        for(PatentList list: patentLists) {
            queue.addAll(list.getPatents());
        }
        patentLists.clear();
        patentLists.add(new PatentList(new ArrayList<>(queue).subList(Math.max(0,queue.size()-limit-1), queue.size()-1)));
    }*/



    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentLists!=null : "Patent list is null!";
        if(avgVector==null) return new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if(patentNamesToExclude ==null) {
            patentNamesToExclude=new HashSet<>();
            if(patentNumber!=null)patentNamesToExclude.add(patentNumber);
        }
        final Set<String> otherSet = Collections.unmodifiableSet(patentNamesToExclude);

        setupMinHeap(limit);
        AtomicInteger i = new AtomicInteger(0);
        List<PatentList> lists = new ArrayList<>();
        names.forEach(name->{
            lists.add(similarPatentsHelper(patentLists.get(i.getAndIncrement()),avgVector, otherSet, name, patentNumber, threshold, limit));
        });

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");

        return lists;
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
            l.flipAvgSimilarity();
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

    private INDArray getVectorFromDB(String patentNumber,INDArray eigenVectors) throws SQLException {
        ResultSet rs = Database.getBaseVectorFor(patentNumber);
        if(!rs.next()) {
            return null; // nothing found
        }
        int offset = 1;
        INDArray avgVector = handleResultSet(rs, offset);
        if(eigenVectors!=null) avgVector.mmuli(eigenVectors);
        return avgVector;
    }

    private INDArray getVectorFromDB(String patentNumber) throws SQLException {
        return getVectorFromDB(patentNumber, null);
    }

    private synchronized PatentList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Set<String> patentNamesToExclude, String name1, String name2, double threshold, int limit) {
        Patent.setBaseVector(baseVector);
        //AtomicDouble total = new AtomicDouble(0.0);
        //AtomicInteger cnt = new AtomicInteger(0);
        patentList.forEach(patent -> {
            if(patent!=null&&!patentNamesToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget();
                //total.getAndAdd(patent.getSimilarityToTarget());
                //cnt.getAndIncrement();
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            //String assignee = assigneeMap.get(p.getName());
            //if(assignee==null)assignee="";
            try {
                resultList.add(0, Patent.abstractClone(p, null));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        //double avgSim = cnt.get() > 0 ? total.get()/cnt.get() : 0.0;
        PatentList results = new PatentList(resultList,name1,name2);
        return results;
    }

    // unit test!
    public static void main(String[] args) throws Exception {
        try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/3"), "othername");
            System.out.println("Most similar: ");
            PatentList list;// = finder.findSimilarPatentsTo("7455590", -1.0, 25).get(0);
            /*for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
            System.out.println("Most opposite: ");
            list = finder.findOppositePatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }*/
            BarnesHutTsne bht = new BarnesHutTsne.Builder()
                    .invertDistanceMetric(false)
                    .learningRate(0.001)
                    .setInitialMomentum(0.5)
                    .setFinalMomentum(0.9)
                    .useAdaGrad(false)
                    .usePca(true)
                    .build();


            System.out.println("Candidate set comparison: ");
            list = finder.similarFromCandidateSet(new SimilarPatentFinder(null, new File("candidateSets/2"), "name"),0.0,20,false).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
