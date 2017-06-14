package ui_models.portfolios.items;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import server.SimilarPatentServer;
import excel.ExcelCell;
import excel.ExcelHandler;
import excel.ExcelRow;
import ui_models.portfolios.PortfolioList;
import ui_models.attributes.value.ValueMapNormalizer;

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
    @Getter @Setter
    protected double value;
    @Getter
    protected List<Pair<String,Double>> technologyList;
    protected PortfolioList.Type type;
    protected Map<String,Object> dataMap = new HashMap<>();

    public static final Comparator<Item> similarityComparator() { return (o1, o2)->Double.compare(o1.getSimilarity(),o2.getSimilarity());}

    public static final Comparator<Item> valueComparator() { return (o1, o2)->Double.compare(o1.getValue(),o2.getValue());}

    public Item(String name) {
        this.name=name;
    }

    public Item clone() {
        return new Item(name);
    }

    public List<Object> getDataAsRow(List<String> attributes) {
        return attributes.stream().map(attr->dataMap.get(attr)).collect(Collectors.toList());
    }

    public void addData(String param, Object data) {
        dataMap.put(param,data);
    }

    @Override
    public int compareTo(Item o) {
        return Double.compare(similarity, o.similarity);
    }
}
