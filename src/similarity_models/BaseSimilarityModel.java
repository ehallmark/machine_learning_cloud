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
import ui_models.portfolios.items.VectorizedItemWrapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/8/2017.
 */
public class BaseSimilarityModel implements AbstractSimilarityModel {
    protected INDArray avgVector;
    @Getter
    protected Collection<VectorizedItemWrapper> items;
    protected Map<String,INDArray> lookupTable;
    protected Map<VectorizedItemWrapper,VectorizedItemWrapper> itemMap;

    @Getter @Setter
    protected String name;

    public BaseSimilarityModel(Collection<Item> candidateSet, String name, Map<String,INDArray> lookupTable) {
        this.lookupTable=lookupTable;
        // construct lists
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        this.name=name;
        try {
            itemMap = candidateSet.stream().map(item->{
                if(!lookupTable.containsKey(item.getName())) return null; // no info on item
                return item;
            }).filter(item->item!=null).map(item->new VectorizedItemWrapper(item,lookupTable.get(item.getName()))).collect(Collectors.toMap(e->e,e->e));
            items = itemMap.keySet();

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            items = Collections.emptyList();
            itemMap = Collections.emptyMap();
        }
    }

    private BaseSimilarityModel() {};

    public INDArray computeAvg() {
        if(avgVector==null) {
            avgVector = Nd4j.vstack(items.stream()
                .map(item->item.getVec()).collect(Collectors.toList())).mean(0);
        }
        return avgVector;
    }

    @Override
    public List<Item> getItemList() {
        return items.stream().map(item->item.getItem()).collect(Collectors.toList());
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters)  {
        if(other.numItems()==0) return new PortfolioList(new ArrayList<>());
        INDArray otherAvg = ((BaseSimilarityModel)other).computeAvg();
        return findSimilarPatentsTo(other.getName(),otherAvg,limit,filters);
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Collection<Item> scope) {
        BaseSimilarityModel model = new BaseSimilarityModel();
        model.name=name;
        model.lookupTable=lookupTable;
        model.itemMap = scope.stream().map(item->itemMap.get(item)).filter(item->item!=null).collect(Collectors.toMap(e->e,e->e));
        model.items = model.itemMap.keySet();
        return new BaseSimilarityModel(scope,name,lookupTable);
    }

    @Override
    public double similarityTo(String label) {
        INDArray avg = computeAvg();
        INDArray thisVec = lookupTable.get(label);
        if(thisVec==null) return 0d;
        else return Transforms.cosineSim(avg,thisVec);
    }

    // returns null if patentNumber not found
    @Override
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters)  {
        assert items!=null : "Item list is null!";
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
        return items==null?0:items.size();
    }

    private synchronized PortfolioList similarPatentsHelper(INDArray baseVector, int limit, Collection<? extends AbstractFilter> filters) {
        MinHeap<WordFrequencyPair<Item,Double>> heap = new MinHeap<>(limit);
        items.forEach(item -> {
            if(item!=null) {
                double sim = Transforms.cosineSim(item.getVec(),baseVector);
                if(!Double.isNaN(sim)) {
                    Item itemClone = item.getItem().clone();
                    itemClone.setSimilarity(sim);
                    // apply item pre filters
                    if (filters.stream().allMatch(filter -> filter.shouldKeepItem(itemClone)))
                        heap.add(new WordFrequencyPair<>(itemClone, sim));
                }
            }
        });
        List<Item> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            WordFrequencyPair<Item,Double> pair = heap.remove();
            Item itemClone = pair.getFirst();
            resultList.add(0, itemClone);
        }
        PortfolioList results = new PortfolioList(resultList);
        return results;
    }

}
