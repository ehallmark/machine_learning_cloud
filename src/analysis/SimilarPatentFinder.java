package analysis;


import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import server.tools.excel.ExcelWritable;
import tools.*;

import java.sql.SQLException;
import java.util.*;

import server.tools.AbstractPatent;

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

    public SimilarPatentFinder(Collection<String> candidateSet, String name, WeightLookupTable<VocabWord> lookupTable) {
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

    public List<PortfolioList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, Collection<String> badAssets, PortfolioList.Type portfolioType) {
        List<PortfolioList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            list.add(similarFromCandidateSet(other, threshold, limit, badAssets, portfolioType));
        });
        return list;
    }

    public PortfolioList similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, Collection<String> badLabels, PortfolioList.Type portfolioType)  {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return null;
        INDArray otherAvg = computeAvg(other.patentList);
        return findSimilarPatentsTo(other.name, otherAvg, badLabels, threshold, limit,portfolioType);
    }

    // returns null if patentNumber not found
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, Collection<String> labelsToExclude, double threshold, int limit, PortfolioList.Type portfolioType)  {
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return null;
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        PortfolioList list = similarPatentsHelper(patentList,avgVector,labelsToExclude, name, patentNumber,threshold,limit,(v1, v2)->Transforms.cosineSim(v1,v2),portfolioType);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");
        return list;
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

    private synchronized PortfolioList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Collection<String> labelsToExclude, String name1, String name2, double threshold, int limit, DistanceFunction dist, PortfolioList.Type portfolioType) {
        Patent.setBaseVector(baseVector);
        patentList.forEach(patent -> {
            if(patent!=null&&!labelsToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget(dist);
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<ExcelWritable> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            ExcelWritable clone = null;
            switch(portfolioType) {
                case assignees: {
                    clone=Patent.abstractAssignee(p,name2);
                    break;
                }case patents: {
                    clone=Patent.abstractPatent(p,name2);
                    break;
                } case class_codes: {
                    clone=Patent.abstractClassCode(p,name2);
                    break;
                }
            }
            if(clone!=null)resultList.add(0, clone);
        }
        PortfolioList results = new PortfolioList(resultList,name1,name2,portfolioType);
        return results;
    }

}
