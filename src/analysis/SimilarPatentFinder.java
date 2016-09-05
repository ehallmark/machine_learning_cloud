package analysis;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.eclipse.jetty.util.ArrayQueue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.*;
import tools.GetTokensThread;
import tools.MinHeap;
import tools.PatentList;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import server.tools.AbstractPatent;
import tools.VectorHelper;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<Patent> patentList;
    protected String name;
    private static TokenizerFactory tf = new DefaultTokenizerFactory();
    private static Map<String,INDArray> globalCache = Collections.synchronizedMap(new HashMap<>());
    static {
        tf.setTokenPreProcessor(new MyPreprocessor());
    }

    //private Map<String, String> assigneeMap;

    public SimilarPatentFinder(Map<String,Pair<Float,INDArray>> vocab) throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE), "**ALL**", vocab);
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException,ClassNotFoundException {
        this(candidateSet,patentListFile,name,null, vocab);
    }

    public SimilarPatentFinder(String name,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        this(name, getVectorFromDB(name, vocab));
    }

    public SimilarPatentFinder(String name, INDArray data) throws SQLException {
        this.name=name;
        patentList = data==null?null:Arrays.asList(new Patent(name, data));
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, INDArray eigenVectors, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        if (!patentListFile.exists()) {
            try {
                if (candidateSet == null) {
                    candidateSet = Database.getValuablePatentsToList();
                }
                int arrayCapacity = candidateSet.size();
                patentList = new ArrayList<>(arrayCapacity);
                // go thru candidate set and remove all that we can find
                List<String> toRemove = new ArrayList<>();
                for(String patent : candidateSet) {
                    if(globalCache.containsKey(patent)) {
                        patentList.add(new Patent(patent,globalCache.get(patent)));
                        toRemove.add(patent);
                        System.out.println("Found: "+patent);
                    }
                }
                candidateSet.removeAll(toRemove);
                if(!candidateSet.isEmpty()) {
                    ResultSet rs;
                    rs = Database.selectPatentVectors(candidateSet);
                    int count = 0;
                    int offset = 2; // Due to the pub_doc_number field
                    while (rs.next()) {
                        try {
                            INDArray array = handleResultSet(rs, offset, vocab);
                            if (array != null) {
                                patentList.add(new Patent(rs.getString(1), array));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        System.out.println(++count);
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            Object obj = ois.readObject();
            patentList = ((List<Patent>) obj);
            // PCA
            if(eigenVectors!=null) patentList.forEach(p->p.getVector().mmuli(eigenVectors));
            ois.close();
        }

        // Now add stuff to the map
        patentList.forEach(p->{
            globalCache.put(p.getName(),p.getVector());
        });

        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public static List<Pair<String,Float>> predictKeywords(int limit, Map<String,Pair<Float,INDArray>> vocab, String patent) throws SQLException {
        ResultSet rs = Database.getBaseVectorFor(patent);
        if(rs.next()) {
            return predictKeywords(rs.getString(1)+" "+rs.getString(2)+" "+rs.getString(3),limit,vocab);
        } else {
            return null;
        }
    }

    private static void processNGrams(List<String> tokens, Map<String,AtomicInteger> nGramCounts, int n) {
        assert n >= 1 : "Cannot process n grams if n < 1!";
        Stemmer stemMe = new Stemmer();
        Iterator<String> tokenIter = tokens.iterator();
        Queue<String> queue = new ArrayQueue<>(n);
        int nullDistance = 0;
        while(tokenIter.hasNext()) {
            String tok = tokenIter.next();
            if(tok==null) {
                // handle nulls
                nullDistance++;
                continue;
            }
            if(nullDistance>3) {
                queue.clear();
                nullDistance=0;
            }
            // not null
            queue.add(tok);
            if(queue.size()==n) {
                if(((int)queue.stream().map(s->stemMe.stem(s)).distinct().count())!=queue.size()) {
                    queue.remove();
                    continue;
                }
                String next = queue.poll();
                assert queue.size()==n-1 : "QUEUE HAS THE WRONG SIZE!";
                while(!queue.isEmpty()) {
                    next=queue.poll()+" "+next;
                }
                if(nGramCounts.containsKey(next)) {
                    nGramCounts.get(next).getAndIncrement();
                } else {
                    nGramCounts.put(next, new AtomicInteger(1));
                }
            }
        }
    }

    public static List<Pair<String,Float>> predictKeywords(String text, int limit, Map<String,Pair<Float,INDArray>> vocab) {
        Map<String,AtomicInteger> nGramCounts = new HashMap<>();
        List<String> tokens = tf.create(text).getTokens().stream().map(s->s!=null&&s.trim().length()>0&&!Constants.STOP_WORD_SET.contains(s)&&vocab.containsKey(s)?s:null).collect(Collectors.toList());
        for(int i = 1; i <= 3; i++) {
            processNGrams(tokens,nGramCounts,i);
        }
        List<Pair<String,Float>> list = nGramCounts.entrySet().stream().map(e->{
            String[] words = e.getKey().split(" ");
            double avgFreq = Arrays.asList(words).stream().collect(Collectors.averagingDouble(s->vocab.get(s).getFirst()));
            Pair<String,Float> newPair = new Pair<>(e.getKey(),(float)(avgFreq*words.length*Math.log(e.getValue().get())));
            return newPair;
        }).sorted((o1,o2)->o2.getSecond().compareTo(o1.getSecond())).collect(Collectors.toList());
        list = list.subList(0,Math.min(limit,list.size()));
        return list;
    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    private static INDArray handleResultSet(ResultSet rs, int offset, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        List<String> tokens = new ArrayList<>();
        String description = rs.getString(offset);
        RecursiveTask<List<String>> descTask = new GetTokensThread(tf,description);
        descTask.fork();
        String abstractText = rs.getString(offset+1);
        RecursiveTask<List<String>> absTask = new GetTokensThread(tf,abstractText);
        absTask.fork();
        String claims = rs.getString(offset+2);
        RecursiveTask<List<String>> claimTask = new GetTokensThread(tf,claims);
        claimTask.fork();
        tokens.addAll(descTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(absTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(claimTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        return VectorHelper.TFIDFcentroidVector(vocab,tokens);
    }

    public String getName() {
        return name;
    }

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
        List<PatentList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                list.addAll(similarFromCandidateSet(other, threshold, limit, findDissimilar));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        return list;
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        INDArray otherAvg = computeAvg(other.patentList);
        Set<String> dontMatch = other.name.equals(this.name) ? null : other.patentList.stream().map(p->p.getName()).collect(Collectors.toSet());
        try {
            if(findDissimilar) lists.addAll(findOppositePatentsTo(other.name, otherAvg, dontMatch, threshold, limit));
            else lists.addAll(findSimilarPatentsTo(other.name, otherAvg, dontMatch, threshold, limit));

        } catch(SQLException sql) {
        }
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
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if(patentNamesToExclude ==null) {
            patentNamesToExclude=new HashSet<>();
            if(patentNumber!=null)patentNamesToExclude.add(patentNumber);
        }
        final Set<String> otherSet = Collections.unmodifiableSet(patentNamesToExclude);

        setupMinHeap(limit);
        List<PatentList> lists = Arrays.asList(similarPatentsHelper(patentList,avgVector, otherSet, name, patentNumber, threshold, limit));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");

        return lists;
    }

    public Double angleBetweenPatents(String name1, String name2, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray first = getVectorFromDB(name1,vocab);
        INDArray second = getVectorFromDB(name2,vocab);
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
    public List<PatentList> findOppositePatentsTo(String patentNumber, double threshold, int limit,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        return findOppositePatentsTo(patentNumber, getVectorFromDB(patentNumber,vocab), null, threshold, limit);
    }


    // returns empty if no results found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, double threshold, int limit,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        return findSimilarPatentsTo(patentNumber, getVectorFromDB(patentNumber,vocab), null, threshold, limit);
    }

    private static INDArray getVectorFromDB(String patentNumber,INDArray eigenVectors,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray avgVector = null;
        // first look in own patent list
        if(globalCache.containsKey(patentNumber)) avgVector=globalCache.get(patentNumber);

        if(avgVector==null) {
            ResultSet rs = Database.getBaseVectorFor(patentNumber);
            if (!rs.next()) {
                return null; // nothing found
            }
            int offset = 1;
            avgVector = handleResultSet(rs, offset, vocab);
            if(avgVector!=null) globalCache.put(patentNumber,avgVector);
        }
        if(eigenVectors!=null&&avgVector!=null) avgVector.mmuli(eigenVectors);
        return avgVector;
    }

    private static INDArray getVectorFromDB(String patentNumber,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        return getVectorFromDB(patentNumber, null, vocab);
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
        /*try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/3"), "othername");
            System.out.println("Most similar: ");
            PatentList list;// = finder.findSimilarPatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
            System.out.println("Most opposite: ");
            list = finder.findOppositePatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }



            System.out.println("Candidate set comparison: ");
            list = finder.similarFromCandidateSet(new SimilarPatentFinder(null, new File("candidateSets/2"), "name"),0.0,20,false).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }*/
    }
}
