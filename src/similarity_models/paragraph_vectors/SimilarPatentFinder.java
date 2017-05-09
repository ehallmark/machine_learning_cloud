package similarity_models.paragraph_vectors;


import lombok.Getter;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import similarity_models.AbstractSimilarityModel;
import ui_models.portfolios.items.Item;
import tools.*;

import java.util.*;

import ui_models.portfolios.PortfolioList;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder implements AbstractSimilarityModel {
    protected MinHeap<Patent> heap;
    protected INDArray avgVector;
    protected List<Patent> patentList;
    protected String name;
    protected int id;
    
    @Getter
    public static final Map<String,INDArray> globalCache = Collections.synchronizedMap(new HashMap<>());
    public void setName(String name) { this.name = name; }

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
                vector = lookupTable.vector(patent);
                if (vector != null) {
                    patentList.add(new Patent(patent, vector));
                    globalCache.put(patent,vector);
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

    public String getName() {
        return name;
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public INDArray computeAvg() {
        if(avgVector!=null) return avgVector;

        INDArray thisAvg = Nd4j.create(patentList.size(), Constants.VECTOR_LENGTH);
        double totalWeight = 0.0;
        for(int i = 0; i < patentList.size(); i++) {
            Patent p = patentList.get(i);
            double weight = Math.max(1.0,(double)(Database.getExactAssetCountFor(p.getName())));
            thisAvg.putRow(i, p.getVector().mul(weight));
            totalWeight+=weight;
        }
        avgVector=thisAvg.sum(0).divi(totalWeight);
        return avgVector;
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
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new PortfolioList(new ArrayList<>(),portfolioType);
        INDArray otherAvg = other.computeAvg();
        return findSimilarPatentsTo(other.name, otherAvg, badLabels, threshold, limit,portfolioType);
    }

    // returns null if patentNumber not found
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, Collection<String> labelsToExclude, double threshold, int limit, PortfolioList.Type portfolioType)  {
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new PortfolioList(new ArrayList<>(),portfolioType);
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        PortfolioList list = similarPatentsHelper(patentList,avgVector,labelsToExclude, patentNumber,threshold,limit,(v1, v2)->Transforms.cosineSim(v1,v2),portfolioType);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getPortfolio().size()+" similar patents: "+time+" seconds");
        return list;
    }

    private synchronized PortfolioList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Collection<String> labelsToExclude, String referringName, double threshold, int limit, DistanceFunction dist, PortfolioList.Type portfolioType) {
        Patent.setBaseVector(baseVector);
        patentList.forEach(patent -> {
            if(patent!=null&&!labelsToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget(dist);
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<Item> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            Item clone = null;
            switch(portfolioType) {
                case assignees: {
                    clone=Patent.abstractAssignee(p,referringName);
                    break;
                }case patents: {
                    clone=Patent.abstractPatent(p,referringName);
                    break;
                }
            }
            if(clone!=null)resultList.add(0, clone);
        }
        PortfolioList results = new PortfolioList(resultList,portfolioType);
        return results;
    }

}
