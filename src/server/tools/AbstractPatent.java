package server.tools;

import seeding.Database;
import tools.AssigneeTrimmer;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent implements Comparable<AbstractPatent>, ExcelWritable{
    private String name;
    private double similarity;

    @Override
    public String[] getDataAsRow(boolean valuePrediction, int tagLimit) {
        if(valuePrediction) {
            return new String[]{
                    name,
                    String.valueOf(similarity),
                    String.valueOf(gatherValue),
                    getFullAssignee(),
                    String.valueOf(Math.min(tagLimit,getTags().size())),
                    getOrderedTags().isEmpty() ? "" : getOrderedTags().get(0),
                    getTags().size() > 1 ? String.join("; ", getOrderedTags().subList(1, Math.min(tagLimit,getOrderedTags().size()))) : "",
                    title
            };
        } else {
            return new String[]{
                    name,
                    String.valueOf(similarity),
                    getFullAssignee(),
                    String.valueOf(Math.min(tagLimit,getTags().size())),
                    getOrderedTags().isEmpty() ? "" : getOrderedTags().get(0),
                    getTags().size() > 1 ? String.join("; ", getOrderedTags().subList(1, Math.min(tagLimit,getOrderedTags().size()))) : "",
                    title
            };
        }
    }

    private Double gatherValue;
    private Map<String,Double> tags;
    private List<String> orderedTags;
    private String title;
    private boolean collateral;
    private String assignee;
    private String collateralAgent;


    public AbstractPatent(String name, double similarity, String referringName) throws SQLException {
        this.name = name;
        this.tags = new HashMap<>();
        this.similarity=similarity;
        this.title= Database.getInventionTitleFor(name);
        this.collateral=false;
        this.collateralAgent=null;
        this.assignee = String.join("; ",Database.assigneesFor(name).stream().map(a->AssigneeTrimmer.standardizedAssignee(a)).collect(Collectors.toList()));
        tags.put(referringName,similarity);

        // collateral agent problem

        // check if this assignee has a lien
        /* int collateralIdx = assignee.lastIndexOf(" collateral");
        if(collateralIdx<0) collateralIdx = assignee.lastIndexOf(" administrative");
        if(collateralIdx<0) collateralIdx = assignee.lastIndexOf(" na,");
        if(collateralIdx<0) collateralIdx = assignee.lastIndexOf(" na ");
        if(collateralIdx<0 && assignee.endsWith(" na")) collateralIdx = assignee.lastIndexOf(" na");
        if(collateralIdx>0) {
            collateral=true;
            int idx;
            if(assignee.lastIndexOf(" na ")>0 && assignee.lastIndexOf(" na ")<collateralIdx) idx = assignee.lastIndexOf(" na ");
            else if(assignee.lastIndexOf(" na,")>0 && assignee.lastIndexOf(" na,")<collateralIdx) idx = assignee.lastIndexOf(" na,");
            else if(assignee.lastIndexOf(" as ")>0 && assignee.lastIndexOf(" as ")<collateralIdx) idx = assignee.lastIndexOf(" as ");
            else idx = collateralIdx;
            collateralAgent = assignee.substring(0,idx).replaceAll(",","").replaceAll("\\.","").trim();
            assignee = Database.selectAssigneeNameFromPatentGrant(name).replaceAll("\\.","").toLowerCase().trim();
        } else {
            collateral=false;
        }

        assignee = assignee.replaceAll(",","");
        // make sure not the same
        if(collateral&&(collateralAgent.contains(this.assignee)||this.assignee.contains(collateralAgent))) {
            collateral=false;
            assignee = "";
            collateralAgent=null;
        }*/
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

    public void setGatherValue(double value) {
        this.gatherValue=value;
    }

    public Double getGatherValue() {
        return gatherValue;
    }

    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }

    public Map<String,Double> getTags() {
        return tags;
    }

    private void calculateOrderedTags() {
        orderedTags = tags.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e1->e1.getKey()).collect(Collectors.toList());
    }

    public List<String> getOrderedTags() {
        if(orderedTags==null) calculateOrderedTags();
        return orderedTags;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getFullAssignee() {
        if(collateral&&collateralAgent!=null) {
            return assignee+" subject to lien ("+collateralAgent+")";
        } else {
            return assignee;
        }
    }

    public void flipSimilarity() { similarity*=-1.0; }

    @Override
    public int compareTo(AbstractPatent o) {
        return Double.compare(similarity, o.similarity);
    }
}
