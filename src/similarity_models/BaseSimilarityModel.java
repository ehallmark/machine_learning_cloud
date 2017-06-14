package similarity_models;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import similarity_models.paragraph_vectors.Patent;
import similarity_models.paragraph_vectors.WordFrequencyPair;
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
    @Getter
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
            itemList = candidateSet.stream().map(itemStr->{
                if(!lookupTable.containsKey(itemStr)) return null; // no info on item
                return new Item(itemStr);
            }).filter(item->item!=null).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            itemList = null;
        }
        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public INDArray computeAvg() {
        if(avgVector!=null) return avgVector;

        INDArray thisAvg = Nd4j.create(itemList.size(), Constants.VECTOR_LENGTH);
        for(int i = 0; i < itemList.size(); i++) {
            Item item = itemList.get(i);
            thisAvg.putRow(i, lookupTable.get(item.getName()));
        }
        avgVector=thisAvg.mean(0);
        return avgVector;
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters)  {
        if(((BaseSimilarityModel)other).getItemList()==null||((BaseSimilarityModel)other).getItemList().isEmpty()) return new PortfolioList(new ArrayList<>());
        INDArray otherAvg = ((BaseSimilarityModel)other).computeAvg();
        return findSimilarPatentsTo(other.getName(),otherAvg,limit,filters);
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Collection<String> scope) {
        return new BaseSimilarityModel(scope,name,lookupTable);
    }

    // returns null if patentNumber not found
    @Override
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters)  {
        assert heap!=null : "Heap is null!";
        assert itemList!=null : "Item list is null!";
        if(avgVector==null) return new PortfolioList(new ArrayList<>());
        long startTime = System.currentTimeMillis();
        PortfolioList list = similarPatentsHelper(avgVector, limit, filters);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getPortfolio().size()+" similar patents: "+time+" seconds");
        return list;
    }

    @Override
    public int numItems() {
        return itemList==null?0:itemList.size();
    }

    private synchronized PortfolioList similarPatentsHelper(INDArray baseVector, int limit, Collection<? extends AbstractFilter> filters) {
        MinHeap<WordFrequencyPair<Item,Double>> heap = new MinHeap<>(limit);
        itemList.forEach(item -> {
            if(item!=null) {
                double sim = Transforms.cosineSim(lookupTable.get(item.getName()),baseVector);
                // apply item pre filters
                if (filters.stream().allMatch(filter -> filter.shouldKeepItem(item))) heap.add(new WordFrequencyPair<>(item,sim));

            }
        });
        List<Item> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            WordFrequencyPair<Item,Double> pair = heap.remove();
            Item itemClone = pair.getFirst().clone(); // make sure to clone item
            itemClone.setSimilarity(pair.getSecond()); // make sure to set similarity
            resultList.add(0, itemClone);
        }
        PortfolioList results = new PortfolioList(resultList);
        return results;
    }

}
