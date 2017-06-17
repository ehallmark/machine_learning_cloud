package ui_models.portfolios.items;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import ui_models.portfolios.PortfolioList;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Item implements Comparable<Item> {
    @Getter
    protected String name;
    @Getter @Setter
    protected double similarity;
    protected Map<String,Object> dataMap = new HashMap<>();

    public static final Comparator<Item> similarityComparator() { return (o1, o2)->Double.compare(o1.getSimilarity(),o2.getSimilarity());}

    public Item(String name) {
        this.name=name;
    }

    public Item clone() {
        Item item = new Item(name);
        item.dataMap=new HashMap<>(dataMap);
        item.similarity=similarity;
        return item;
    }

    public void init() {
        addData(Constants.SIMILARITY,getSimilarity());
        addData(Constants.NAME,getName());
    }

    public List<Object> getDataAsRow(List<String> attributes) {
        return attributes.stream().map(attr->dataMap.get(attr)).collect(Collectors.toList());
    }

    public Object getData(String param) {
        return dataMap.get(param);
    }

    public void addData(String param, Object data) {
        dataMap.put(param,data);
    }

    @Override
    public int compareTo(Item o) {
        return Double.compare(similarity, o.similarity);
    }
}
