package ui_models.attributes.value;

import graphical_models.page_rank.PageRank;
import graphical_models.page_rank.PageRankHelper;
import ui_models.portfolios.AbstractPortfolio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/9/17.
 */
public class PageRankEvaluator extends ValueAttr {
    public PageRankEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal, "Page Rank");
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        Map<String,Float> map = new HashMap<>(new PageRank.Loader().loadRankTable(PageRankHelper.file));
        map.forEach((k,v)->{
            System.out.println(k+": "+v);
        });
        return Arrays.asList(new HashMap<>(map.entrySet().stream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue().doubleValue()))));
    }

    // Returns value between 0 and 1
    @Override
    public Double attributesFor(AbstractPortfolio portfolio, int n) {
        System.out.println("Model: "+model);
        System.out.println("Portfolio size: "+portfolio.getTokens().size());
        for (String s : portfolio.getTokens()) {
            System.out.println("Token: "+s);
        }
        return portfolio.getTokens().stream().collect(Collectors.averagingDouble(token->{
            System.out.println("Token 2: "+token);
            if(model.containsKey(token)) {
                return model.get(token);
            } else {
                return 0d;
            }
        }));
    }
}
