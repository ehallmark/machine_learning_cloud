package user_interface.ui_models.templates;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class SimilarAssetSearch extends FormTemplate {

    public SimilarAssetSearch() {
        super(Constants.SIMILAR_PATENT_SEARCH, getParams(), FormTemplate.defaultOptions());
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.REMAINING_LIFE));
        map.put(Constants.LINE_CHART,Arrays.asList(Constants.PRIORITY_DATE));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY,Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE));
        map.put(Constants.REMAINING_LIFE+Constants.FILTER_SUFFIX, 5);
        map.put(Constants.PORTFOLIO_SIZE+"_min"+Constants.FILTER_SUFFIX, 10);
        map.put(Constants.PORTFOLIO_SIZE+"_max"+Constants.FILTER_SUFFIX, 1000);
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.SIMILARITY,Constants.AI_VALUE,Constants.NAME,Constants.LATEST_ASSIGNEE,Constants.REMAINING_LIFE,Constants.PORTFOLIO_SIZE,Constants.CPC_TECHNOLOGY,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD,"");
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD,"");
        map.put(Constants.CLAIMS+Constants.FILTER_SUFFIX,"");
        map.put(Constants.INVENTION_TITLE+ AbstractFilter.FilterType.AdvancedKeyword.toString() + Constants.FILTER_SUFFIX,"");
        map.put(Constants.ABSTRACT+ AbstractFilter.FilterType.AdvancedKeyword.toString() + Constants.FILTER_SUFFIX,"");
        map.put(Constants.DOC_TYPE + AbstractFilter.FilterType.Include.toString() + Constants.FILTER_SUFFIX, Arrays.asList(PortfolioList.Type.patents.toString()));
        return map;
    }
}
