package server.tools.excel;

import jxl.write.WritableCellFormat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public abstract class ExcelWritable implements Comparable<ExcelWritable> {
    protected Map<String,Double> tags = new HashMap<>();
    protected List<String> orderedTags;
    private static final double DEFAULT_CELL_HEIGHT = 24;
    protected Map<String, ExcelCell> attributeData =new HashMap<>();
    protected String name;
    protected Double avgValue;
    protected double similarity;

    protected ExcelWritable(String name, double similarity, String referringName) {
        this.name=name;
        this.similarity=similarity;
        this.tags = new HashMap<>();
        if(referringName!=null)tags.put(referringName,similarity);
    }

    protected void init() {
        calculateOrderedTags();
        String tag = "";
        List<String> tags = getOrderedTags();
        if(!tags.isEmpty()) {
            tag=tags.get(0);
        }
        attributeData.put("primaryTag",new ExcelCell(ExcelHandler.getDefaultFormat(),tag,false));
        attributeData.put("name",new ExcelCell(ExcelHandler.getDefaultFormat(),name,false));
        attributeData.put("similarity",new ExcelCell(ExcelHandler.getPercentageFormat(),similarity,true));
        if(avgValue!=null)attributeData.put("gatherValue",new ExcelCell(ExcelHandler.getDefaultFormat(),avgValue,true));
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

    public void setAttribute(String attr, Object value, WritableCellFormat format) {
        attributeData.put(attr,new ExcelCell(format,value,value instanceof Number));
    }
    public ExcelRow getDataAsRow(List<String> attributes) {
        init();
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
    public int compareTo(ExcelWritable o) {
        return Double.compare(similarity, o.similarity);
    }
}
