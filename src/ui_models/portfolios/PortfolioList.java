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
    private List<Item> itemList;
    private double avgSimilarity;
    public enum Type { patents, assignees }
    private boolean init = false;

    public PortfolioList(List<Item> itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public void applyFilters(Collection<AbstractFilter> filters) {
        itemList=itemList.stream().filter(obj->filters.stream().allMatch(filter->{
            return filter.shouldKeepItem(obj);
        })).collect(Collectors.toList());
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
        if(other.itemList.isEmpty()) {
            newList = new PortfolioList(itemList);
            newList.init(comparator,limit);
            return newList;
        } else if (itemList.isEmpty()) {
            newList = new PortfolioList(other.itemList);
            newList.init(comparator,limit);
            return newList;
        } else {
            Map<String, Item> scoreMap = new HashMap<>();
            this.getItemList().forEach(item -> {
                scoreMap.put(item.getName(), item);
            });
            other.getItemList().forEach(item -> {
                if (scoreMap.containsKey(item.getName())) {
                    Item dup = scoreMap.get(item.getName());
                    dup.setSimilarity((dup.getSimilarity() + item.getSimilarity()) / 2d);
                } else {
                    scoreMap.put(item.getName(), item);
                }
            });
            newList = new PortfolioList(new ArrayList<>(scoreMap.values()));
        }
        newList.init(comparator, limit);
        return newList;
    }


    public void init(String sortedBy, int limit) {
        if(!init) {
            Collections.sort(itemList, (i1, i2) -> {
                return (Double.compare(((Number) (i2.getData(sortedBy))).doubleValue(), ((Number) (i1.getData(sortedBy))).doubleValue()));
            });

            if (itemList.size() > 0) {
                itemList = itemList.subList(0, Math.min(itemList.size(), limit));
                this.avgSimilarity = itemList.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
            } else this.avgSimilarity = 0.0d;
        }
        init=true;
    }
}
