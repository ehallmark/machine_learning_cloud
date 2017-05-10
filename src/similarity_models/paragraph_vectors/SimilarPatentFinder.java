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
import ui_models.filters.AbstractFilter;
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
        candidateSet=new HashSet<>(candidateSet);
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

    public List<PortfolioList> similarFromCandidateSets(List<? extends AbstractSimilarityModel> others, PortfolioList.Type portfolioType, int limit, Collection<? extends AbstractFilter> filters) {
        List<PortfolioList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            list.add(similarFromCandidateSet(other, portfolioType, limit, filters));
        });
        return list;
    }

    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, PortfolioList.Type portfolioType, int limit, Collection<? extends AbstractFilter> filters)  {
        // Find the highest (pairwise) assets
        if(((SimilarPatentFinder)other).getPatentList()==null||((SimilarPatentFinder)other).getPatentList().isEmpty()) return new PortfolioList(new ArrayList<>(),portfolioType);
        INDArray otherAvg = ((SimilarPatentFinder)other).computeAvg();
        return findSimilarPatentsTo(((SimilarPatentFinder)other).name, otherAvg,limit,portfolioType,filters);
    }

    // returns null if patentNumber not found
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, PortfolioList.Type portfolioType, Collection<? extends AbstractFilter> filters)  {
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new PortfolioList(new ArrayList<>(),portfolioType);
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        PortfolioList list = similarPatentsHelper(patentList,avgVector, patentNumber,limit,(v1, v2)->Transforms.cosineSim(v1,v2),portfolioType, filters);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getPortfolio().size()+" similar patents: "+time+" seconds");
        return list;
    }

    private synchronized PortfolioList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, String referringName, int limit, DistanceFunction dist, PortfolioList.Type portfolioType, Collection<? extends AbstractFilter> filters) {
        Patent.setBaseVector(baseVector);
        patentList.forEach(patent -> {
            if(patent!=null) {
                patent.calculateSimilarityToTarget(dist);
                // apply item filters
                Item clone;
                switch(portfolioType) {
                    case assignees: {
                        clone=Patent.abstractAssignee(patent,referringName);
                        break;
                    }case patents: {
                        clone=Patent.abstractPatent(patent,referringName);
                        break;
                    }default: {
                        clone=null;
                        break;
                    }
                }
                if(clone!=null) {
                    if (filters.stream().allMatch(filter -> filter.shouldKeepItem(clone))) heap.add(patent);
                }
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
