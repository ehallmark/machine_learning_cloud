package ui_models.portfolios.items;

import seeding.Database;
import excel.ExcelCell;
import excel.ExcelHandler;
import tools.AssigneeTrimmer;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent extends Item {
    private String assignee;
    private String title;
    public AbstractPatent(String name, double similarity, String referringName) {
        super(name,similarity,referringName, PortfolioList.Type.patents);
        this.title=Database.getInventionTitleFor(name);
        this.assignee = String.join("; ",Database.assigneesFor(name).stream().map(a->AssigneeTrimmer.standardizedAssignee(a)).collect(Collectors.toList()));
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
    public void init(Collection<String> params) {
        super.init(params);
        if(params.contains("assignee"))attributeData.put("assignee", new ExcelCell(ExcelHandler.getDefaultFormat(),assignee,false));
        if(params.contains("title"))attributeData.put("title", new ExcelCell(ExcelHandler.getDefaultFormat(),title,false));
        if(params.contains("assigneeEntityType")) attributeData.put("assigneeEntityType", new ExcelCell(ExcelHandler.getDefaultFormat(),Database.entityTypeForPatent(name),false));
        // override isNumber
        if(attributeData.containsKey("name")) {
            ExcelCell cell = attributeData.get("name");
            attributeData.put("name",new ExcelCell(cell.getFormat(),cell.getContent(),true));
        }
    }

    public String getInventionTitle() { return title; }

    public String getAssignee() {
        return assignee;
    }

}
