package server.tools;

import analysis.tech_tagger.GatherTagger;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;
import server.tools.excel.ExcelCell;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import tools.AssigneeTrimmer;
import tools.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class AbstractAssignee extends ExcelWritable {
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
