package user_interface.ui_models.portfolios;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter @Setter
    private List<Item> itemList;
    private double avgSimilarity;
    public enum Type { patents, applications }
    private boolean init = false;

    public PortfolioList(List<Item> itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }


    public Stream<Pair<String,PortfolioList>> groupedBy(String field) {
        if(field==null) return Arrays.asList(new Pair<>("",this)).stream();
        return itemList.stream().collect(Collectors.groupingBy((item)->(item).getData(field))).entrySet()
                .stream().map(e->new Pair<>(e.getKey().toString(),new PortfolioList(e.getValue())));
    }

    public void init(String sortedBy, int limit) {
        if(!init) {
            itemList = itemList.parallelStream().sorted((i1,i2)-> (Double.compare(((Number) (i2.getData(sortedBy))).doubleValue(), ((Number) (i1.getData(sortedBy))).doubleValue()))).collect(Collectors.toList());
            if (itemList.size() > 0) {
                itemList = itemList.subList(0,Math.min(itemList.size(), limit));
                //this.avgSimilarity = itemList.parallelStream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
            } else this.avgSimilarity = 0.0d;
        }
        init=true;
    }
}
