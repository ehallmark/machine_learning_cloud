package user_interface.ui_models.portfolios;

import elasticsearch.DataSearcher;
import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
        return itemList.stream().filter(item->item.getData(field)!=null).flatMap(item->{
            // check for array separator
            Object r = item.getData(field);
            Stream<Object> objStream;
            if (r != null) {
                if (r instanceof Collection) {
                    objStream = ((Collection) r).stream();
                } else if (r.toString().contains(DataSearcher.ARRAY_SEPARATOR)) {
                    objStream = Stream.of(r.toString().split(DataSearcher.ARRAY_SEPARATOR));
                } else {
                    objStream = Stream.of(r);
                }
            } else {
                objStream = Stream.empty();
            }
            return objStream.filter(obj->obj!=null).map(obj->new Pair<>(item,obj));
        }).collect(Collectors.groupingBy(pair->pair.getSecond(),Collectors.mapping(pair->pair.getFirst(), Collectors.toList()))).entrySet()
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

    public String[] getIds() {
        return itemList.stream().map(item->item.getName()).toArray(size->new String[size]);
    }
}
