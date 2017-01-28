package server.tools;

import seeding.Database;
import server.tools.excel.ExcelCell;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;

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
    protected void init() {
        super.init();
        attributeData.put("totalAssetCount", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.selectPatentNumbersFromClassCode(name).size(),true));
        attributeData.put("title", new ExcelCell(ExcelHandler.getDefaultFormat(),Database.getClassTitleFromClassCode(name),false));
    }

}
