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
import java.util.stream.Stream;

/**
 * Created by Evan on 6/8/2017.
 */
public class BaseSimilarityModel implements AbstractSimilarityModel {
    protected INDArray avgVector;
    @Getter
    protected Collection<VectorizedItemWrapper> items;
    protected Map<String,INDArray> lookupTable;
    protected Map<VectorizedItemWrapper,VectorizedItemWrapper> itemMap;
    protected Item[] itemList;

    @Getter @Setter
    protected String name;

    public BaseSimilarityModel(Collection<Item> candidateSet, String name, Map<String,INDArray> lookupTable) {
        this.lookupTable=lookupTable;
        // construct lists
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        this.name=name;
        try {
            itemMap = candidateSet.parallelStream().map(item->{
                if(!lookupTable.containsKey(item.getName())) return null; // no info on item
                return item;
            }).filter(item->item!=null).map(item->new VectorizedItemWrapper(item,lookupTable.get(item.getName()))).collect(Collectors.toMap(e->e,e->e));
            items = itemMap.keySet();
            itemMap = Collections.synchronizedMap(itemMap);

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            items = Collections.emptyList();
            itemMap = Collections.emptyMap();
        }
        itemList = items.stream().map(item->item.getItem()).toArray(size->new Item[size]);
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
    public Item[] getItemList() {
        return itemList;
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters)  {
        if(other.numItems()==0) return new PortfolioList(new Item[]{});
        INDArray otherAvg = ((BaseSimilarityModel)other).computeAvg();
        return findSimilarPatentsTo(other.getName(),otherAvg,limit,filters);
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Item[] scope) {
        BaseSimilarityModel model = new BaseSimilarityModel();
        model.name=name;
        model.lookupTable=lookupTable;
        model.itemMap = Arrays.stream(scope).map(item->itemMap.get(item)).filter(item->item!=null).collect(Collectors.toMap(e->e,e->e));
        model.items = model.itemMap.keySet();
        model.itemList = model.items.toArray(new Item[model.items.size()]);
        return model;
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
        if(avgVector==null) return new PortfolioList(new Item[]{});
        long startTime = System.currentTimeMillis();
        PortfolioList list = similarPatentsHelper(avgVector, limit, filters);
        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find "+list.getItemList().length+" similar patents: "+time+" seconds");
        return list;
    }

    @Override
    public int numItems() {
        return items==null?0:items.size();
    }

    private synchronized PortfolioList similarPatentsHelper(INDArray baseVector, int limit, Collection<? extends AbstractFilter> filters) {
        Stream<Item> resultStream = items.parallelStream().map(item->{
            double sim = Transforms.cosineSim(item.getVec(),baseVector);
            if(!Double.isNaN(sim)) {
                Item itemClone = item.getItem().clone();
                itemClone.setSimilarity(sim);
                return itemClone;
            } else return null;
        }).filter(item->item!=null&&filters.stream().allMatch(filter->filter.shouldKeepItem(item)));
        Item[] resultList = resultStream.toArray(size->new Item[size]);
        if(resultList.length==0) return new PortfolioList(resultList);
        Arrays.parallelSort(resultList,(i1,i2)->Double.compare(i2.getSimilarity(),i1.getSimilarity()));
        PortfolioList results = new PortfolioList(Arrays.copyOfRange(resultList,0, Math.min(limit,resultList.length)));
        return results;
    }

}
