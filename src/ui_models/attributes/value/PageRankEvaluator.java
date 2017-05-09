package ui_models.attributes.value;

import graphical_models.page_rank.PageRank;
import graphical_models.page_rank.PageRankHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/9/17.
 */
public class PageRankEvaluator extends ValueAttr {
    public PageRankEvaluator() {
        super(ValueMapNormalizer.DistributionType.None, "Page Rank");
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return Arrays.asList(new PageRank.Loader().loadRankTable(PageRankHelper.file).entrySet().stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().doubleValue())));
    }
}
