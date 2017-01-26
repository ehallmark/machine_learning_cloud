package analysis;


import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.suffix.ConcurrentSuffixTree;
import com.googlecode.concurrenttrees.suffix.SuffixTree;
import edu.stanford.nlp.util.Quadruple;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.eclipse.jetty.util.ArrayQueue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.*;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.*;
import tools.*;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import server.tools.AbstractPatent;
import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<Patent> patentList;
    protected String name;
    protected int id;

    private static Map<String,INDArray> globalCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,List<String>> patentWordsCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,INDArray> globalCandidateAvgCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,List<WordFrequencyPair<String,Float>>> patentKeywordCache = Collections.synchronizedMap(new HashMap<>());

    public void setName(String name) { this.name = name; }

    public static Map<String,INDArray> getGlobalCache() {
        return globalCache;
    }

    public static void clearCaches() {
        patentKeywordCache.clear();
        globalCandidateAvgCache.clear();
        patentWordsCache.clear();
        globalCache.clear();
    }

    public SimilarPatentFinder(Collection<String> candidateSet, String name, WeightLookupTable<VocabWord> lookupTable) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        try {
            int arrayCapacity = candidateSet.size();
            patentList = new ArrayList<>(arrayCapacity);
            // go thru candidate set and remove all that we can find
            List<String> toRemove = new ArrayList<>();
            for (String patent : candidateSet) {
                if (globalCache.containsKey(patent)) {
                    patentList.add(new Patent(patent, globalCache.get(patent)));
                    toRemove.add(patent);
                }
            }
            candidateSet.removeAll(toRemove);
            for(String patent : candidateSet) {
                INDArray vector;
                vector = handleResultSet(patent,lookupTable);
                if (vector != null) {
                    patentList.add(new Patent(patent, vector));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            patentList = null;
        }
        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    public static INDArray handleResultSet(String label, WeightLookupTable<VocabWord> lookupTable) {
        return lookupTable.vector(label);
    }

    public String getName() {
        return name;
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public static INDArray computeAvg(List<Patent> patentList) {
        INDArray thisAvg = Nd4j.create(patentList.size(),patentList.stream().findFirst().get().getVector().columns());
        for(int i = 0; i < patentList.size(); i++) {
            thisAvg.putRow(i, patentList.get(i).getVector());
        }
        INDArray avg = thisAvg.mean(0);
        return avg;
    }

    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, Collection<String> badAssets) throws SQLException {
        List<PatentList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                list.addAll(similarFromCandidateSet(other, threshold, limit, badAssets));
            } catch(Exception sql) {
                sql.printStackTrace();
            }
        });
        return list;
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, Collection<String> badLabels) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        INDArray otherAvg = computeAvg(other.patentList);
        try {
            lists.addAll(findSimilarPatentsTo(other.name, otherAvg, badLabels, threshold, limit));

        } catch(SQLException sql) {
        }
        return lists;
    }

    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Collection<String> labelsToExclude, double threshold, int limit) throws SQLException {
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new ArrayList<>();
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        List<PatentList> lists = Arrays.asList(similarPatentsHelper(patentList,avgVector,labelsToExclude, name, patentNumber,threshold,limit,(v1,v2)->Transforms.cosineSim(v1,v2)));
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");
        return lists;
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

    public static INDArray getVectorFromDB(String patentNumber,WeightLookupTable<VocabWord> lookupTable) {
        INDArray avgVector = null;
        // first look in own patent list
        if(globalCache.containsKey(patentNumber)) avgVector=globalCache.get(patentNumber);

        if(avgVector==null)  { // use words to approximate vec
            avgVector = handleResultSet(patentNumber, lookupTable);
            if(avgVector!=null) globalCache.put(patentNumber,avgVector);
        }

        return avgVector;
    }

    private synchronized PatentList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Collection<String> labelsToExclude, String name1, String name2, double threshold, int limit, DistanceFunction dist) {
        Patent.setBaseVector(baseVector);
        patentList.forEach(patent -> {
            if(patent!=null&&!labelsToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget(dist);
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            resultList.add(0, Patent.abstractClone(p, name2));
        }
        PatentList results = new PatentList(resultList,name1,name2);
        return results;
    }

}
