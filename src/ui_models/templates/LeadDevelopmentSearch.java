package ui_models.templates;

import seeding.Constants;
import server.SimilarPatentServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class LeadDevelopmentSearch extends FormTemplate {

    public LeadDevelopmentSearch() {
        super(Constants.LEAD_DEVELOPMENT_SEARCH, getParams(), FormTemplate.similarityAssigneeSmall(), Arrays.asList(SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD,SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD,SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD));
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.AI_VALUE,Constants.SIMILARITY));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.ASSIGNEE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER, 10);
        map.put(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER, 1000);
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.AI_VALUE,Constants.ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD,"");
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD,"");
        map.put(SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD,new ArrayList<>());
        return map;
    }
}
