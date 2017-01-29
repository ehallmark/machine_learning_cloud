package server.tools.excel;

import jxl.write.WritableCellFormat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public abstract class ExcelWritable implements Comparable<ExcelWritable> {
    protected Map<String,Double> tags;
    protected List<String> orderedTags;
    private static final double DEFAULT_CELL_HEIGHT = 24;
    protected Map<String, ExcelCell> attributeData =new HashMap<>();
    protected String name;
    protected double classValue;
    protected double noveltyValue;
    protected double citationValue;
    protected double similarity;

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        humanAttrToJavaAttrMap=new HashMap<>();
        humanAttrToJavaAttrMap.put("Asset","name");
        humanAttrToJavaAttrMap.put("Similarity","similarity");
        humanAttrToJavaAttrMap.put("Classification Value","classValue");
        humanAttrToJavaAttrMap.put("Citation Value","citationValue");
        humanAttrToJavaAttrMap.put("Relevant Asset(s)","relevantAssetsList");
        humanAttrToJavaAttrMap.put("Relevant Asset Count","relevantAssetCount");
        humanAttrToJavaAttrMap.put("Total Asset Count","totalAssetCount");
        humanAttrToJavaAttrMap.put("Assignee","assignee");
        humanAttrToJavaAttrMap.put("Title","title");
        humanAttrToJavaAttrMap.put("Class Codes","classCode");
        humanAttrToJavaAttrMap.put("Novelty Value","noveltyValue");


        // inverted version to get human readables back
        javaAttrToHumanAttrMap= new HashMap<>();
        humanAttrToJavaAttrMap.forEach((k,v)->javaAttrToHumanAttrMap.put(v,k));
    }

    public static String humanAttributeFor(String attr) {
        if(javaAttrToHumanAttrMap.containsKey(attr)) {
            return javaAttrToHumanAttrMap.get(attr);
        } else {
            return "";
        }
    }

    public static Map<String,String> getHumanAttrToJavaAttrMap() {
        return Collections.unmodifiableMap(humanAttrToJavaAttrMap);
    }

    protected ExcelWritable(String name, double similarity, String referringName) {
        this.name=name;
        this.similarity=similarity;
        this.tags = new HashMap<>();
        if(referringName!=null)tags.put(referringName,similarity);
    }

    public void setClassValue(double classValue) {
        this.classValue=classValue;
    }

    public void setCitationValue(double citationValue) {
        this.citationValue=citationValue;
    }

    protected void init(Collection<String> params) {
        calculateOrderedTags();
        String tag = "";
        List<String> tags = getOrderedTags();
        if(!tags.isEmpty()) {
            tag=tags.get(0);
        }
        if(params.contains("primaryTag"))attributeData.put("primaryTag",new ExcelCell(ExcelHandler.getDefaultFormat(),tag,false));
        if(params.contains("name"))attributeData.put("name",new ExcelCell(ExcelHandler.getDefaultFormat(),name,false));
        if(params.contains("similarity"))attributeData.put("similarity",new ExcelCell(ExcelHandler.getPercentageFormat(),similarity,true));
        if(params.contains("citationValue"))attributeData.put("citationValue",new ExcelCell(ExcelHandler.getDefaultFormat(),citationValue,true));
        if(params.contains("classValue"))attributeData.put("classValue",new ExcelCell(ExcelHandler.getDefaultFormat(),classValue,true));
        if(params.contains("noveltyValue"))attributeData.put("noveltyValue",new ExcelCell(ExcelHandler.getDefaultFormat(),noveltyValue,true));
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

    public void setNoveltyValue(double noveltyValue) {
        this.noveltyValue=noveltyValue;
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
