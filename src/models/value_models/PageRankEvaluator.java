package models.value_models;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

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
