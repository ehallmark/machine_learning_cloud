package server.tools;

import analysis.tech_tagger.GatherTagger;
import seeding.Database;
import server.tools.excel.ExcelCell;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import tools.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class AbstractClassCode extends ExcelWritable {
    public AbstractClassCode(String name, double similarity, String referringName) {
        super(name,similarity,referringName, PortfolioList.Type.class_codes);
    }

    @Override
    protected void init(Collection<String> params) {
        super.init(params);
        attributeData.put("totalAssetCount", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.selectPatentNumbersFromClassAndSubclassCodes(name).size(),true));
        attributeData.put("title", new ExcelCell(ExcelHandler.getDefaultFormat(),Database.getClassTitleFromClassCode(name),false));
    }

}
