package user_interface.ui_models.portfolios;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter @Setter
    private Item[] itemList;
    private double avgSimilarity;
    public enum Type { patents, applications }
    private boolean init = false;

    public PortfolioList(Item[] itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public void ensureAttributesArePresent(Collection<? extends ComputableAttribute> attributes)  {
        for(Item item : itemList) {
            for(ComputableAttribute attribute : attributes) {
                if(item.getData(attribute.getName())==null) {
                    item.addData(attribute.getName(), attribute.attributesFor(Arrays.asList(item.getName()), 1));
                }
            }
        }
    }

    public Stream<Pair<String,PortfolioList>> groupedBy(String field) {
        if(field==null) return Arrays.asList(new Pair<>("",this)).stream();
        return Arrays.stream(itemList).collect(Collectors.groupingBy((item)->(item).getData(field))).entrySet()
                .stream().map(e->new Pair<>(e.getKey().toString(),new PortfolioList(e.getValue().toArray(new Item[e.getValue().size()]))));
    }

    public void init(String sortedBy, int limit) {
        if(!init) {
            Arrays.parallelSort(itemList,(i1,i2)-> (Double.compare(((Number) (i2.getData(sortedBy))).doubleValue(), ((Number) (i1.getData(sortedBy))).doubleValue())));
            if (itemList.length > 0) {
                itemList = Arrays.copyOfRange(itemList, 0, Math.min(itemList.length, limit));
                this.avgSimilarity = Arrays.stream(itemList).parallel().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
            } else this.avgSimilarity = 0.0d;
        }
        init=true;
    }
}
