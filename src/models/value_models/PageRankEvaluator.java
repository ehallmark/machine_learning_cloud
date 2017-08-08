package models.value_models;

import models.graphical_models.page_rank.PageRank;
import models.graphical_models.page_rank.PageRankHelper;
import seeding.Constants;
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
    public PageRankEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    @Override
    public String getName() {
        return Constants.PAGE_RANK_VALUE;
    }
}
