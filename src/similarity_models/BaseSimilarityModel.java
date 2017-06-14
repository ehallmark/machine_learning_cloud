package similarity_models;

import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import similarity_models.paragraph_vectors.WordFrequencyPair;
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
    protected INDArray avgVector;
    @Getter
    protected Collection<String> tokens;
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
            tokens = candidateSet.stream().map(itemStr->{
                if(!lookupTable.containsKey(itemStr)) return null; // no info on item
                return itemStr;
            }).filter(item->item!=null).collect(Collectors.toSet());

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            tokens = Collections.emptyList();
        }
        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public INDArray computeAvg() {
        if(avgVector==null) {
            avgVector = Nd4j.vstack(tokens.stream()
                .map(token->lookupTable.get(token))
                .filter(vec->vec!=null).collect(Collectors.toList())).mean(0);
        }
        return avgVector;
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters)  {
        if(other.getTokens()==null||other.getTokens().isEmpty()) return new PortfolioList(new ArrayList<>());
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
        assert tokens!=null : "Item list is null!";
        if(avgVector==null) return new PortfolioList(new ArrayList<>());
        long startTime = System.currentTimeMillis();
        PortfolioList list = similarPatentsHelper(avgVector, limit, filters);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getItemList().size()+" similar patents: "+time+" seconds");
        return list;
    }

    @Override
    public int numItems() {
        return tokens==null?0:tokens.size();
    }

    private synchronized PortfolioList similarPatentsHelper(INDArray baseVector, int limit, Collection<? extends AbstractFilter> filters) {
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(limit);
        tokens.forEach(item -> {
            if(item!=null) {
                double sim = Transforms.cosineSim(lookupTable.get(item),baseVector);
                // apply item pre filters
                if (filters.stream().allMatch(filter -> filter.shouldKeepItem(new Item(item)))) heap.add(new WordFrequencyPair<>(item,sim));

            }
        });
        List<Item> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            WordFrequencyPair<String,Double> pair = heap.remove();
            Item itemClone = new Item(pair.getFirst()); // make sure to clone item
            itemClone.setSimilarity(pair.getSecond()); // make sure to set similarity
            resultList.add(0, itemClone);
        }
        PortfolioList results = new PortfolioList(resultList);
        return results;
    }

}
