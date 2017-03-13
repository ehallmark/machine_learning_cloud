package server.tools.excel;

import analysis.tech_tagger.TechTagger;
import analysis.tech_tagger.TechTaggerNormalizer;
import org.deeplearning4j.berkeley.Pair;
import server.SimilarPatentServer;
import tools.PortfolioList;
import value_estimation.ValueMapNormalizer;

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
    protected double similarity;
    protected Map<String,Double> valueMap = new HashMap<>();
    protected String technology;
    protected List<Pair<String,Double>> technologyList;
    protected PortfolioList.Type type;
    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        humanAttrToJavaAttrMap=new HashMap<>();
        humanAttrToJavaAttrMap.put("Asset","name");
        humanAttrToJavaAttrMap.put("Similarity","similarity");
        humanAttrToJavaAttrMap.put("Relevant Asset(s)","relevantAssetsList");
        humanAttrToJavaAttrMap.put("Relevant Asset Count","relevantAssetCount");
        humanAttrToJavaAttrMap.put("Total Asset Count","totalAssetCount");
        humanAttrToJavaAttrMap.put("Assignee","assignee");
        humanAttrToJavaAttrMap.put("Title","title");
        humanAttrToJavaAttrMap.put("Primary Tag","primaryTag");
        humanAttrToJavaAttrMap.put("AI Value","overallValue");
        humanAttrToJavaAttrMap.put("Technology","technology");
        humanAttrToJavaAttrMap.put("Market Value","marketValue");
        humanAttrToJavaAttrMap.put("Assignee Entity Type","assigneeEntityType");
        humanAttrToJavaAttrMap.put("Large Portfolio Size","largePortfolios");
        humanAttrToJavaAttrMap.put("Small Portfolio Size","smallPortfolios");
        humanAttrToJavaAttrMap.put("Assets Sold","assetsSold");
        humanAttrToJavaAttrMap.put("Assets Purchased", "assetsPurchased");


        // inverted version to get human readables back
        javaAttrToHumanAttrMap= new HashMap<>();
        humanAttrToJavaAttrMap.forEach((k,v)->javaAttrToHumanAttrMap.put(v,k));
    }

    public static final Comparator<ExcelWritable> similarityComparator() { return (o1,o2)->Double.compare(o1.getSimilarity(),o2.getSimilarity());}

    public static final Comparator<ExcelWritable> valueComparator() { return (o1,o2)->Double.compare(o1.getAvgValue(),o2.getAvgValue());}

    public static String humanAttributeFor(String attr) {
        if(javaAttrToHumanAttrMap.containsKey(attr)) {
            return javaAttrToHumanAttrMap.get(attr);
        } else {
            return attr;
        }
    }

    public static Map<String,String> getHumanAttrToJavaAttrMap() {
        return Collections.unmodifiableMap(humanAttrToJavaAttrMap);
    }

    protected ExcelWritable(String name, double similarity, String referringName, PortfolioList.Type type) {
        this.name=name;
        this.similarity=similarity;
        this.type=type;
        this.tags = new HashMap<>();
        if(referringName!=null)tags.put(referringName,similarity);
    }

    public void setValue(String valueType, double value) {
        this.valueMap.put(valueType,value);
    }

    public double getAvgValue() {
        if(valueMap.isEmpty()) return ValueMapNormalizer.DEFAULT_START;
        else {
            return valueMap.entrySet().stream().collect(Collectors.averagingDouble(e->e.getValue()));
        }
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
        if(params.contains("similarity"))attributeData.put("similarity",new ExcelCell(ExcelHandler.getPercentageFormat(),String.format("%.4f",similarity),true));
        //add value params if available
        valueMap.forEach((type,value)->{
            if(params.contains(type)) {
                attributeData.put(type,new ExcelCell(ExcelHandler.getValueFormat(),String.format("%.2f",value),true));
            }
        });
        if(params.contains("technology")) {
            technologyList = SimilarPatentServer.getTagger().getTechnologiesFor(name, type,5);
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
    public int compareTo(ExcelWritable o) {
        return Double.compare(similarity, o.similarity);
    }
}
