package models.value_models;

import models.graphical_models.page_rank.PageRank;
import models.graphical_models.page_rank.PageRankHelper;
import seeding.Database;
import tools.DateHelper;
import user_interface.ui_models.attributes.ValueAttr;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 5/9/17.
 */
public class PageRankEvaluator extends ValueAttr {
    public PageRankEvaluator(boolean loadData) {
        super(ValueMapNormalizer.DistributionType.Normal, "Page Rank", loadData);
        DateHelper.addScoresToAssigneesFromPatents(Database.getAssignees(), model);
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        Map<String,Float> map = new PageRank.Loader().loadRankTable(PageRankHelper.file);
        Map<String,Double> doubleMap = new HashMap<>(map.size());
        map.forEach((k,v)->{
            //if(Database.isPatent(k)) {
                doubleMap.put(k, v.doubleValue());
            //}
        });
        return Arrays.asList(doubleMap);
    }

}
