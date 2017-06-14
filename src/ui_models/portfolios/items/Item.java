package ui_models.portfolios.items;

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
public abstract class Item implements Comparable<Item> {
    protected Map<String,Double> tags;
    protected List<String> orderedTags;
    private static final double DEFAULT_CELL_HEIGHT = 24;
    protected Map<String, ExcelCell> attributeData =new HashMap<>();
    protected String name;
    protected double similarity;
    protected Map<String,Double> valueMap = new HashMap<>();
    protected String technology;
    protected List<Pair<String,Double>> technologyList;
    protected PortfolioList.Type type;

    static {

    }

    public static final Comparator<Item> similarityComparator() { return (o1, o2)->Double.compare(o1.getSimilarity(),o2.getSimilarity());}

    public static final Comparator<Item> valueComparator() { return (o1, o2)->Double.compare(o1.getAvgValue(),o2.getAvgValue());}

    public static String humanAttributeFor(String attr) {
        if(javaAttrToHumanAttrMap.containsKey(attr))  {
            return javaAttrToHumanAttrMap.get(attr);
        } else {
            return attr;
        }
    }

    public static Map<String,String> getHumanAttrToJavaAttrMap() {
        return Collections.unmodifiableMap(humanAttrToJavaAttrMap);
    }

    public static Map<String,String> getHumanFilterToJavaFilterMap() {
        return Collections.unmodifiableMap(humanFilterToJavaFilterMap);
    }

    protected Item(String name, double similarity, PortfolioList.Type type) {
        this.name=name;
        this.similarity=similarity;
        this.type=type;
    }

    public void setValue(String valueType, double value) {
        this.valueMap.put(valueType,value);
    }

    public void init(Collection<String> params) {
        calculateOrderedTags();
        String tag = "";
        List<String> tags = getOrderedTags();
        if(!tags.isEmpty()) {
            tag=tags.get(0);
        }
        if(params.contains("primaryTag"))attributeData.put("primaryTag",new ExcelCell(ExcelHandler.getDefaultFormat(),tag,false));
        if(params.contains("name"))attributeData.put("name",new ExcelCell(ExcelHandler.getDefaultFormat(),name,false));
        if(params.contains("similarity"))attributeData.put("similarity",new ExcelCell(ExcelHandler.getPercentageFormat(),String.format("%.4f",similarity),true));
        //add value params if available
        valueMap.forEach((type,value)->{
            if(params.contains(type)) {
                attributeData.put(type,new ExcelCell(ExcelHandler.getValueFormat(),String.format("%.2f",value),true));
            }
        });
        if(params.contains("technology")) {
            technologyList = SimilarPatentServer.getTagger().attributesFor(PortfolioList.asList(name,type),5);
            if(technologyList.isEmpty())technology="";
            else technology=technologyList.get(0).getFirst();
            attributeData.put("technology", new ExcelCell(ExcelHandler.getDefaultFormat(), technology, false));
        }
    }
    public Map<String,Double> getTags() {
        return tags;
    }

    private void calculateOrderedTags() {
        orderedTags=tags.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e1->e1.getKey()).collect(Collectors.toList());
    }

    public List<String> getOrderedTags() {
        if(orderedTags==null) calculateOrderedTags();
        return orderedTags;
    }

    public ExcelRow getDataAsRow(List<String> attributes) {
        init(attributes);
        List<ExcelCell> cells = new ArrayList<>();
        for(String attr : attributes) {
            if(attr!=null&&attributeData.containsKey(attr)) {
                cells.add(attributeData.get(attr));
            } else {
                cells.add(null);
            }
        }
        return new ExcelRow(cells,DEFAULT_CELL_HEIGHT);
    }

    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double sim) {
        similarity=sim;
    }

    public void appendTags(Map<String,Double> newTags) {
        newTags.entrySet().forEach(e -> {
            if (tags.containsKey(e.getKey())) {
                tags.put(e.getKey(), Math.max(e.getValue(), tags.get(e.getKey())));
            } else {
                tags.put(e.getKey(), e.getValue());
            }
        });
    }

    @Override
    public int compareTo(Item o) {
        return Double.compare(similarity, o.similarity);
    }
}
