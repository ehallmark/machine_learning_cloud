package similarity_models;

import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import similarity_models.paragraph_vectors.Patent;
import tools.DistanceFunction;
import tools.MinHeap;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/8/2017.
 */
public class BaseSimilarityModel implements AbstractSimilarityModel {
    protected MinHeap<Patent> heap;
    protected INDArray avgVector;
    protected List<Item> itemList;
    protected Map<String,INDArray> lookupTable;
    @Getter @Setter
    protected String name;

    public BaseSimilarityModel(Collection<String> candidateSet, String name, Map<String,INDArray> lookupTable) {
        this.lookupTable=lookupTable;
        // construct lists
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        candidateSet=candidateSet.stream().distinct().collect(Collectors.toList());
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        try {
            itemList = candidateSet.stream().map(item->{
                INDArray vector = lookupTable.get(item);
                return new Item(item, vector);
            }).filter(item->item!=null).collect(Collectors.toList());


        } catch (Exception e) {
            e.printStackTrace();
            // errors
            itemList = null;
        }
        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public List<Patent> getPatentList() {
        return patentList;
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

    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters)  {
        // Find the highest (pairwise) assets
        if(((BaseSimilarityModel)other).getPatentList()==null||((BaseSimilarityModel)other).getPatentList().isEmpty()) return new PortfolioList(new ArrayList<>());
        INDArray otherAvg = ((BaseSimilarityModel)other).computeAvg();
        return findSimilarPatentsTo(other.getName(), otherAvg,limit,filters);
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Collection<String> scope) {
        return new BaseSimilarityModel(scope,name,lo)
    }

    // returns null if patentNumber not found
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters)  {
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new PortfolioList(new ArrayList<>());
        long startTime = System.currentTimeMillis();
        setupMinHeap(limit);
        PortfolioList list = similarPatentsHelper(patentList,avgVector, patentNumber,limit,(v1, v2)-> Transforms.cosineSim(v1,v2), filters);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getPortfolio().size()+" similar patents: "+time+" seconds");
        return list;
    }

    @Override
    public int numItems() {
        return patentList==null?0:patentList.size();
    }

    private synchronized PortfolioList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, String referringName, int limit, DistanceFunction dist, Collection<? extends AbstractFilter> filters) {
        patentList.forEach(patent -> {
            if(patent!=null) {
                double sim = Transforms.cosineSim(patent.getVector(),baseVector);
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
            resultList.add(0, clone);
        }
        PortfolioList results = new PortfolioList(resultList);
        return results;
    }

}
