package ui_models.portfolios;

import lombok.Getter;
import lombok.Setter;
import ui_models.attributes.AbstractAttribute;
import ui_models.exceptions.AttributeException;
import ui_models.exceptions.FilterException;
import ui_models.exceptions.SortingException;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.attributes.DoNothing;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public void applyFilters(Collection<AbstractFilter> filters) throws FilterException {
        Set<String> errorNames = new HashSet<>();
        itemList=itemList.stream().filter(obj->filters.stream().allMatch(filter->{
            try {
                return filter.shouldKeepItem(obj);
            } catch(Exception e) {
                e.printStackTrace();
                errorNames.add(filter.getName());
                return false;
            }
        })).collect(Collectors.toList());
        if(errorNames.size()>0) {
            throw new FilterException(String.join("; ",errorNames));
        }
    }

    public void applyAttributes(Collection<? extends AbstractAttribute> attributes) throws AttributeException {
        for(Item item : itemList) {
            for(AbstractAttribute attribute : attributes) {
                if(!(attribute instanceof DoNothing)) {
                    try {
                        item.addData(attribute.getName(), attribute.attributesFor(Arrays.asList(item.getName()), 1));
                    } catch(Exception e) {
                        e.printStackTrace();
                        throw new AttributeException(attribute.getName());
                    }
                }
            }
        }
    }


    public void init(String sortedBy, int limit) throws SortingException {
        try {
            Collections.sort(itemList,(i1,i2)->(Double.compare(((Number)(i2.getData(sortedBy))).doubleValue(),((Number)(i1.getData(sortedBy))).doubleValue())));
        } catch(Exception e) {
            e.printStackTrace();
            throw new SortingException(sortedBy);
        }

        if (itemList.size() > 0) {
            itemList=itemList.subList(0,Math.min(itemList.size(),limit));
            this.avgSimilarity = itemList.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
