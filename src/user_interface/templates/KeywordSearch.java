package user_interface.templates;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class KeywordSearch extends FormTemplate {

    public KeywordSearch() {
        super(Constants.KEYWORD_SEARCH, getParams(), FormTemplate.value(), Arrays.asList(Constants.ADVANCED_KEYWORD_FILTER,Constants.REQUIRE_KEYWORD_FILTER, Constants.EXCLUDE_KEYWORD_FILTER));
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.AI_VALUE));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.ASSIGNEE, Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.NAME,Constants.AI_VALUE,Constants.ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.REMAINING_LIFE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(Constants.ADVANCED_KEYWORD_FILTER,"");
        map.put(Constants.REQUIRE_KEYWORD_FILTER,"");
        map.put(Constants.EXCLUDE_KEYWORD_FILTER,"");
        map.put(SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD, Arrays.asList(PortfolioList.Type.patents,PortfolioList.Type.applications));
        return map;
    }
}
