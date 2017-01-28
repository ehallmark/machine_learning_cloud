package server.tools;

import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;
import server.tools.excel.ExcelCell;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;
import tools.AssigneeTrimmer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/19/16.
 */
public class AbstractAssignee extends ExcelWritable {
    public AbstractAssignee(String name, double similarity, String referringName) {
        this.name = name;
        this.tags = new HashMap<>();
        if (referringName != null) tags.put(referringName, similarity);
    }

        @Override
    protected void init() {
        super.init();
        attributeData.put("totalAssetCount", new ExcelCell(ExcelHandler.getDefaultFormat(), Database.getAssetCountFor(name),true));
    }

}
