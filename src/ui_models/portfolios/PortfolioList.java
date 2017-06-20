package ui_models.portfolios;

import lombok.Getter;
import lombok.Setter;
import ui_models.attributes.AbstractAttribute;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.attributes.DoNothing;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter @Setter
    private List<Item> itemList;
    private double avgSimilarity;
    public enum Type { patents, assignees }

    public PortfolioList(List<Item> itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public List<String> getTokens() {
        return itemList.stream().map(p->p.getName()).collect(Collectors.toList());
    }

    public void applyFilters(Collection<AbstractFilter> filters) {
        itemList=itemList.stream().filter(obj->filters.stream().allMatch(filter->filter.shouldKeepItem(obj))).collect(Collectors.toList());
    }

    public void applyAttributes(Collection<? extends AbstractAttribute> attributes) {
        itemList.forEach(item->{
            attributes.forEach(attribute->{
                if(!(attribute instanceof DoNothing))
                item.addData(attribute.getName(), attribute.attributesFor(Arrays.asList(item.getName()), 1));
            });
        });
    }


    public void init(String sortedBy, int limit) {
        Collections.sort(itemList,(i1,i2)->(Double.compare(((Number)(i2.getData(sortedBy))).doubleValue(),((Number)(i1.getData(sortedBy))).doubleValue())));

        if (itemList.size() > 0) {
            itemList=itemList.subList(0,Math.min(itemList.size(),limit));
            this.avgSimilarity = itemList.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
