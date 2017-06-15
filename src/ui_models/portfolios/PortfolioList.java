package ui_models.portfolios;

import lombok.Getter;
import ui_models.attributes.AbstractAttribute;
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

    public void applyFilter(AbstractFilter filter) {
        itemList=itemList.stream().filter(obj->filter.shouldKeepItem(obj)).collect(Collectors.toList());
    }

    public void applyAttribute(AbstractAttribute attribute) {
        itemList.forEach(item->{
            item.addData(attribute.getName(),attribute.attributesFor(Arrays.asList(item.getName()),1));
        });
    }

    public void init(String sortedBy, int limit) {
        Collections.sort(itemList,(i1,i2)->(Double.compare(((Number)(i2.getData(sortedBy))).doubleValue(),((Number)(i1.getData(sortedBy))).doubleValue())));
        itemList=itemList.subList(0,Math.min(limit,itemList.size()));
        itemList.forEach(item->{
            item.init();
        });
        if (itemList.size() > 0) {
            this.avgSimilarity = itemList.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
