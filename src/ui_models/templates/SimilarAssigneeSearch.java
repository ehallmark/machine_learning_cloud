package ui_models.templates;

import seeding.Constants;
import server.SimilarPatentServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class SimilarAssigneeSearch extends FormTemplate {

    public SimilarAssigneeSearch() {
        super(Constants.SIMILAR_ASSIGNEE_SEARCH, getParams(), FormTemplate.similarityAssigneeSmall(), Arrays.asList(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD));
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.SIMILARITY));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER, 10);
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.AI_VALUE,Constants.ASSIGNEE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD,"");
        return map;
    }
}
