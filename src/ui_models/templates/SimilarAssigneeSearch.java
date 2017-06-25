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
        super(Constants.SIMILAR_ASSIGNEE_SEARCH, getParams(), FormTemplate.similarityAssigneeSmall());
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.VALUE_MODELS_ARRAY_FIELD, Arrays.asList(Constants.AI_VALUE));
        map.put(SimilarPatentServer.CHART_MODELS_ARRAY_FIELD, Arrays.asList(Constants.PIE_CHART, Arrays.asList(Constants.HISTOGRAM)));
        map.put(Constants.HISTOGRAM,Constants.SIMILARITY);
        map.put(Constants.PIE_CHART,Constants.ASSIGNEE);
        map.put(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER, 10);
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.ASSIGNEE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD,"");
        return map;
    }
}
