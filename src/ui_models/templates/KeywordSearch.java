package ui_models.templates;

import seeding.Constants;
import server.SimilarPatentServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class KeywordSearch extends FormTemplate {

    public KeywordSearch() {
        super(Constants.KEYWORD_SEARCH, getParams(), FormTemplate.valuePatentSmall(), Arrays.asList(Constants.REQUIRE_KEYWORD_FILTER));
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.AI_VALUE));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.ASSIGNEE, Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.NAME,Constants.AI_VALUE,Constants.ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.REMAINING_LIFE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.PATENTS_TO_SEARCH_IN_FIELD,"");
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD,"");
        return map;
    }
}
