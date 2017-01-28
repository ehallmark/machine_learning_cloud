package server.tools;

import seeding.Database;
import server.tools.excel.ExcelCell;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import tools.AssigneeTrimmer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent extends ExcelWritable {
    private String assignee;
    private String title;
    public AbstractPatent(String name, double similarity, String referringName) {
        this.name=name;
        this.tags = new HashMap<>();
        this.title=Database.getInventionTitleFor(name);
        this.assignee = String.join("; ",Database.assigneesFor(name).stream().map(a->AssigneeTrimmer.standardizedAssignee(a)).collect(Collectors.toList()));
        if(referringName!=null)tags.put(referringName,similarity);

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

    @Override
    protected void init() {
        super.init();
        attributeData.put("assignee", new ExcelCell(ExcelHandler.getDefaultFormat(),assignee,false));
        attributeData.put("title", new ExcelCell(ExcelHandler.getDefaultFormat(),title,false));
    }

    public String getInventionTitle() { return title; }

    public String getAssignee() {
        return assignee;
    }

    public String getFullAssignee() {
        return getAssignee();
        // Collateral Agent handling
        /*if(collateral&&collateralAgent!=null) {
            return assignee+" subject to lien ("+collateralAgent+")";
        } else {
            return assignee;
        }*/
    }

    public void flipSimilarity() { setSimilarity(getSimilarity()*-1.0); }
}
