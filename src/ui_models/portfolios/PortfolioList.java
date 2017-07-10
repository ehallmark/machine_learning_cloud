package ui_models.portfolios;

import lombok.Getter;
import lombok.Setter;
import seeding.Constants;
import ui_models.attributes.AbstractAttribute;
import ui_models.exceptions.AttributeException;
import ui_models.exceptions.FilterException;
import ui_models.exceptions.SortingException;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter
    private Item[] itemList;
    private double avgSimilarity;
    public enum Type { patents, assignees }
    private boolean init = false;

    public PortfolioList(Item[] itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public void applyFilters(Collection<AbstractFilter> filters) {
        itemList=Arrays.stream(itemList).parallel().filter(obj->filters.stream().allMatch(filter->{
            return filter.shouldKeepItem(obj);
        })).toArray(size-> new Item[size]);
    }

    public void applyAttributes(Collection<? extends AbstractAttribute> attributes)  {
        for(Item item : itemList) {
            for(AbstractAttribute attribute : attributes) {
                item.addData(attribute.getName(), attribute.attributesFor(Arrays.asList(item.getName()), 1));
            }
        }
    }

    public PortfolioList merge(PortfolioList other, String comparator, int limit) {
        PortfolioList newList;
        if(other.itemList.length==0) {
            newList = this;
        } else if (itemList.length==0) {
            newList = other;
        } else {
            Map<String, Item> scoreMap = new HashMap<>();
            for(Item item : this.getItemList()) {
                scoreMap.put(item.getName(), item);
            }
            for(Item item : other.getItemList()) {
                if (scoreMap.containsKey(item.getName())) {
                    Item dup = scoreMap.get(item.getName());
                    dup.setSimilarity((dup.getSimilarity() + item.getSimilarity()) / 2d);
                } else {
                    scoreMap.put(item.getName(), item);
                }
            }
            newList = new PortfolioList(scoreMap.values().toArray(new Item[scoreMap.size()]));
        }
        newList.init(comparator, limit);
        return newList;
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
