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
        super(name,similarity,referringName);
    }

    @Override
    protected void init(Collection<String> params) {
        super.init(params);
        attributeData.put("totalAssetCount", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.selectPatentNumbersFromClassAndSubclassCodes(name).size(),true));
        attributeData.put("title", new ExcelCell(ExcelHandler.getDefaultFormat(),Database.getClassTitleFromClassCode(name),false));
        if(params.contains("technology")) {
            technologyList = GatherTagger.getTechnologiesFor(name, PortfolioList.Type.class_codes,5);
            if(technologyList.isEmpty())technology="";
            else technology=technologyList.get(0).getFirst();
            attributeData.put("technology", new ExcelCell(ExcelHandler.getDefaultFormat(), technology, false));
        }
    }

}
