package ui_models.portfolios.items;

import seeding.Database;
import excel.ExcelCell;
import excel.ExcelHandler;
import ui_models.portfolios.PortfolioList;

import java.util.*;

/**
 * Created by ehallmark on 11/19/16.
 */
public class AbstractAssignee extends Item {
    public AbstractAssignee(String name, double similarity, String referringName) {
        super(name,similarity,referringName, PortfolioList.Type.assignees);
    }

    @Override
    protected void init(Collection<String> params) {
        super.init(params);
        if(params.contains("assignee"))attributeData.put("assignee",new ExcelCell(ExcelHandler.getDefaultFormat(), name, false));
        if(params.contains("totalAssetCount"))attributeData.put("totalAssetCount", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.getAssetCountFor(name), true));
        if (params.contains("assigneeEntityType"))
            attributeData.put("assigneeEntityType", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.assigneeEntityType(name), false));
    }
}
