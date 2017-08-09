package models.similarity_models;

import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.VectorizedItemWrapper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/8/2017.
 */
public class BaseSimilarityModel implements AbstractSimilarityModel {
    protected INDArray avgVector;
    protected Map<String,INDArray> lookupTable;
    protected Map<String,VectorizedItemWrapper> itemMap;
    protected Item[] itemList;

    public BaseSimilarityModel(Collection<Item> candidateSet, Map<String,INDArray> lookupTable) {
        this.lookupTable=lookupTable;
        // construct lists
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        try {
            itemMap = candidateSet.parallelStream().map(item->new VectorizedItemWrapper(item,lookupTable.get(item.getName())))
                    .filter(vec->vec.getVec()!=null).collect(Collectors.toMap(e->e.getItem().getName(),e->e));
            itemMap = Collections.synchronizedMap(itemMap);

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            itemMap = Collections.emptyMap();
        }
        itemList = itemMap.values().stream().map(item->item.getItem()).toArray(size->new Item[size]);
    }

    public INDArray computeAvg() {
        if(avgVector==null) {
            avgVector = Nd4j.vstack(itemMap.values().stream()
                .map(item->item.getVec()).collect(Collectors.toList())).mean(0);
        }
        return avgVector;
    }

    @Override
    public Item[] getItemList() {
        return itemList;
    }


    @Override
    public int numItems() {
        return itemList==null?0:itemList.length;
    }

}
