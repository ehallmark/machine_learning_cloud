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

    public void setID(int id) {
        this.id=id;
    }

    public int getID() {
        return id;
    }

    public static Map<String,INDArray> getGlobalCache() {
        return globalCache;
    }

    public static void clearCaches() {
        patentKeywordCache.clear();
        globalCandidateAvgCache.clear();
        patentWordsCache.clear();
        globalCache.clear();
    }

    public SimilarPatentFinder(WeightLookupTable<VocabWord> lookupTable) throws SQLException, IOException, ClassNotFoundException {
        this(null, new File("globalSimilarPatentFinder.obj"), "**ALL**", lookupTable);
    }

    public SimilarPatentFinder(Collection<String> candidateSet, File patentListFile, String name, WeightLookupTable<VocabWord> lookupTable) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        if(patentListFile!=null&&patentListFile.exists()){
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            Object obj = ois.readObject();
            patentList = ((List<String>) obj).stream()
                .map(patent->new Patent(patent,lookupTable.vector(patent)))
                .filter(patent->patent.getVector()!=null)
                .collect(Collectors.toList());
            // PCA
            ois.close();

        } else {
            try {
                if (candidateSet == null) {
                    candidateSet = Database.getValuablePatents();
                }
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
                patentListFile = null;
            }

            if (patentListFile != null) {
                // Serialize List
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
                oos.writeObject(patentList.stream()
                    .map(patent->patent.getName())
                    .collect(Collectors.toList()));
                oos.flush();
                oos.close();
            }
        }


        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    public static INDArray handleResultSet(String label, WeightLookupTable<VocabWord> lookupTable) {
        Set<String> classifications = null;
        try {
            classifications = Database.classificationsFor(label);
        } catch(Exception e) {
            e.printStackTrace();
        }
        Set<String> inventors = null;
        try {
            inventors = Database.assigneesFor(label);
        } catch(Exception e) {
            e.printStackTrace();
        }
        try {
            INDArray pVector = lookupTable.vector(label);
            if(pVector==null) return null;
            INDArray classVector;
            try {
                classVector = Nd4j.vstack(classifications.stream()
                        .map(klass -> lookupTable.vector(klass))
                        .filter(vec -> vec != null)
                        .collect(Collectors.toList())).mean(0);
            } catch(Exception e) {
                classVector=pVector.dup();
            }

            INDArray inventorVector;
            try {
                inventorVector = Nd4j.vstack(inventors.stream()
                        .map(inventor -> lookupTable.vector(inventor))
                        .filter(vec -> vec != null)
                        .collect(Collectors.toList())).mean(0);
            } catch(Exception e) {
                inventorVector=pVector.dup();
            }

            return Nd4j.hstack(pVector,classVector,inventorVector);

        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getName() {
        return name;
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public static INDArray computeAvg(List<Patent> patentList, String candidateSetName) {
        if(candidateSetName!=null&&globalCandidateAvgCache.containsKey(candidateSetName)) {
            return globalCandidateAvgCache.get(candidateSetName);
        }
        INDArray thisAvg = Nd4j.create(patentList.size(),patentList.stream().findFirst().get().getVector().columns());
        for(int i = 0; i < patentList.size(); i++) {
            thisAvg.putRow(i, patentList.get(i).getVector());
        }
        INDArray avg = thisAvg.mean(0);
        if(candidateSetName!=null) {
            globalCandidateAvgCache.put(candidateSetName,avg);
        }
        return avg;
    }

    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, boolean findDissimilar, Integer minPatentNum, Set<String> badAssets, boolean allowResultsFromOtherCandidateSet) throws SQLException {
        List<PatentList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                list.addAll(similarFromCandidateSet(other, threshold, limit, findDissimilar,minPatentNum, badAssets,allowResultsFromOtherCandidateSet));
            } catch(Exception sql) {
                sql.printStackTrace();
            }
        });
        return list;
    }

    public static double predictGatherValue(AbstractPatent aPatent, List<SimilarPatentFinder> gatherValueFinders) {
        String gatherPrefix = "Gather Ratings - ";
        gatherValueFinders.forEach(other->{
            assert other.getName().startsWith(gatherPrefix) : "Invalid other similar patent finder!";
        });
        List<Pair<Integer,INDArray>> avgVectors = gatherValueFinders.stream().map(spf->new Pair<>(Integer.valueOf(spf.getName().replaceFirst(gatherPrefix,"")),computeAvg(spf.getPatentList(),spf.getName()))).collect(Collectors.toList());
        final int numTags = 3;
        Patent patent = new Patent(aPatent.getName(),globalCache.get(aPatent.getName()));
        Patent patentToAdd = new Patent(patent.getName(), patent.getVector());
        patentToAdd.setSimilarity(0.0);
        AtomicDouble normalizationContant = new AtomicDouble(0.0);
        AtomicInteger inclusionFactor = new AtomicInteger(numTags);
        AtomicDouble simToTop = new AtomicDouble(0.0);
        DistanceFunction dist = (v1,v2) -> Transforms.cosineSim(v1,v2);
        avgVectors.stream().map(other-> {
            Patent.setBaseVector(other.getSecond());
            patent.calculateSimilarityToTarget(dist);
            if(other.getFirst().equals(5)) simToTop.set(patent.getSimilarityToTarget());
            return new WordFrequencyPair<>(other.getFirst(), patent.getSimilarityToTarget());
        }).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(numTags).forEach(other->{
            double val = other.getSecond()*Math.pow(inclusionFactor.getAndDecrement(),inclusionFactor.get());
            normalizationContant.addAndGet(val);
            patentToAdd.incrementSimilarityBy(val*other.getFirst());
        });
        return simToTop.get()*patentToAdd.getSimilarityToTarget()/(normalizationContant.get());
    }

    public List<PatentList> gatherValuePrediction(List<SimilarPatentFinder> others, int limit) throws SQLException {
        String gatherPrefix = "Gather Ratings - ";
        others.forEach(other->{
            assert other.getName().startsWith(gatherPrefix) : "Invalid other similar patent finder!";
        });
        setupMinHeap(limit);
        //DistanceFunction dist = (v1,v2)->-1.0*Math.log(v1.div(v1.norm2Number()).distance2(v2.div(v2.norm2Number())));
        DistanceFunction dist = (v1,v2) -> Transforms.cosineSim(v1,v2);
        List<Pair<Integer,INDArray>> avgVectors = others.stream().map(spf->new Pair<>(Integer.valueOf(spf.getName().replaceFirst(gatherPrefix,"")),computeAvg(spf.getPatentList(),spf.getName()))).collect(Collectors.toList());
        AtomicDouble avgSim = new AtomicDouble(0.0);
        final int numTags = 3;
        patentList.forEach(patent->{
            Patent patentToAdd = new Patent(patent.getName(),patent.getVector());
            patentToAdd.setSimilarity(0.0);
            AtomicDouble normalizationContant = new AtomicDouble(0.0);
            AtomicInteger inclusionFactor = new AtomicInteger(numTags);
            AtomicDouble simToTop = new AtomicDouble(0.0);
            avgVectors.stream().map(other-> {
                Patent.setBaseVector(other.getSecond());
                patent.calculateSimilarityToTarget(dist);
                if(other.getFirst().equals(5)) simToTop.set(patent.getSimilarityToTarget());
                return new WordFrequencyPair<>(other.getFirst(), patent.getSimilarityToTarget());
            }).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(numTags).forEach(other->{
                double val = other.getSecond()*Math.pow(inclusionFactor.getAndDecrement(),inclusionFactor.get());
                normalizationContant.addAndGet(val);
                patentToAdd.incrementSimilarityBy(val*other.getFirst());
            });
            heap.add(patentToAdd);
            patentToAdd.setSimilarity(simToTop.get()*patentToAdd.getSimilarityToTarget()/(normalizationContant.get()));
            avgSim.getAndAdd(patentToAdd.getSimilarityToTarget());
        });

        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            try {
                resultList.add(0, Patent.abstractClone(p, name));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        return Arrays.asList(new PatentList(resultList,name,"Gather Ranking"));
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar, Integer minPatentNum, Set<String> badAssets, boolean allowResultsFromOtherCandidateSet) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        INDArray otherAvg = computeAvg(other.patentList,other.getName());
        Set<String> dontMatch = badAssets;
        if(!(other.name.equals(this.name) || allowResultsFromOtherCandidateSet)) other.patentList.forEach(p->dontMatch.add(p.getName()));
        try {
            if(findDissimilar) lists.addAll(findOppositePatentsTo(other.name, otherAvg, dontMatch, threshold, limit,minPatentNum));
            else lists.addAll(findSimilarPatentsTo(other.name, otherAvg, dontMatch, threshold, limit,minPatentNum));

        } catch(SQLException sql) {
        }
        return lists;
    }

    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit, Integer minPatentNum) throws SQLException {
        return findSimilarPatentsTo(patentNumber, avgVector, patentNamesToExclude, threshold, limit, (v1,v2)->Transforms.cosineSim(v1,v2), minPatentNum);
    }

    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit, DistanceFunction dist, Integer minPatentNum) throws SQLException {
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
        List<PatentList> lists = Arrays.asList(similarPatentsHelper(patentList,avgVector, otherSet, name, patentNumber, threshold, limit,dist,minPatentNum));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");

        return lists;
    }

    public Double angleBetweenPatents(String name1, String name2, WeightLookupTable<VocabWord> lookupTable) throws SQLException {
        INDArray first = tryFindParagraphVectors(name1,lookupTable);
        INDArray second = tryFindParagraphVectors(name2,lookupTable);
        if(first!=null && second!=null) {
            //valid
            return Transforms.cosineSim(first,second);
        } else return null;
    }

    // returns null if patentNumber not found
    public List<PatentList> findOppositePatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit, Integer minPatentNum) throws SQLException {
        List<PatentList> toReturn = findSimilarPatentsTo(patentNumber, avgVector.mul(-1.0), patentNamesToExclude, threshold, limit, minPatentNum);
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

    protected static INDArray tryFindParagraphVectors(String paragraphID,WeightLookupTable<VocabWord> lookupTable) {
        try {
            return lookupTable.vector(paragraphID);
        } catch(Exception e) {
            //e.printStackTrace();
            return null;
        }
    }

    private synchronized PatentList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Set<String> patentNamesToExclude, String name1, String name2, double threshold, int limit, DistanceFunction dist, Integer minDate) {
        Patent.setBaseVector(baseVector);
        patentList.stream()
                .filter(p->{
                    if(minDate==null) return true;
                    try{
                        if(Integer.valueOf(p.getName())>=minDate) return true;
                    } catch(Exception e) {

                    }
                    return false;
                }).forEach(patent -> {
                    if(patent!=null&&!patentNamesToExclude.contains(patent.getName())) {
                        patent.calculateSimilarityToTarget(dist);
                        if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
                    }
                });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            try {
                resultList.add(0, Patent.abstractClone(p, name2));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        //double avgSim = cnt.get() > 0 ? total.get()/cnt.get() : 0.0;
        PatentList results = new PatentList(resultList,name1,name2);
        return results;
    }

}
