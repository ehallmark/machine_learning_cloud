package user_interface.ui_models.portfolios.items;

import lombok.Getter;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import models.value_models.ValueMapNormalizer;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.td;

/**
 * Created by ehallmark on 11/19/16.
 */
public class Item implements Comparable<Item> {
    @Getter
    protected Map<String,Object> dataMap = new HashMap<>();
    protected Double similarityCache;
    protected String name;

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
        return similarityCache == null ? ValueMapNormalizer.DEFAULT_START : similarityCache;
    }

    public void setName(String name) {
        this.name=name;
        addData(Constants.NAME,name);
    }

    public String getName() {
        return name;
    }

    public List<String> getDataAsRow(List<String> attributes) {
        return attributes.stream().map(attr->{
            Object cell = dataMap.get(attr);
            return cell==null? "": ((cell instanceof Double || cell instanceof Float) ? (((Number)cell).doubleValue()==(double) ((Number)cell).intValue() ? String.valueOf(((Number)cell).intValue()) : String.format("%.1f",cell)) : cell.toString());
        }).collect(Collectors.toList());
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

    @Override
    public boolean equals(Object other) {
        return name.equals(other.toString());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
