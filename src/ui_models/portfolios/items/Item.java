package ui_models.portfolios.items;

import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import ui_models.attributes.value.ValueMapNormalizer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Item implements Comparable<Item> {
    protected Map<String,Object> dataMap = new HashMap<>();
    protected Double similarityCache;

    public static final Comparator<Item> similarityComparator() { return (o1, o2)->Double.compare(o1.getSimilarity(),o2.getSimilarity());}

    public Item(String name) {
        setName(name);
    }

    public Item clone() {
        Item item = new Item(getName());
        item.dataMap=new HashMap<>(dataMap);
        return item;
    }

    public void setSimilarity(double sim) {
        this.similarityCache=sim;
        addData(Constants.SIMILARITY, sim*100d);
    }

    public double getSimilarity() {
        if(similarityCache==null) similarityCache = (Double) dataMap.getOrDefault(Constants.SIMILARITY,null);
        return similarityCache == null ? ValueMapNormalizer.DEFAULT_START : similarityCache;
    }

    public void setName(String name) {
        addData(Constants.NAME,name);
    }

    public String getName() {
        return (String) dataMap.getOrDefault(Constants.NAME, null);
    }

    public List<Pair<String,Object>> getDataAsRow(List<String> attributes) {
        return attributes.stream().map(attr->new Pair<>(attr,dataMap.get(attr))).collect(Collectors.toList());
    }

    public Object getData(String param) {
        return dataMap.get(param);
    }

    public void addData(String param, Object data) {
        dataMap.put(param,data);
    }

    @Override
    public int compareTo(Item o) {
        return Double.compare(getSimilarity(), o.getSimilarity());
    }
}
