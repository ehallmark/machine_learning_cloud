package ui_models.templates;

import seeding.Constants;
import server.SimilarPatentServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class AssigneeAssessment extends FormTemplate {

    public AssigneeAssessment() {
        super(Constants.ASSIGNEE_ASSESSMENT, getParams(), FormTemplate.valuePatentSmall(), Arrays.asList(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD));
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.VALUE_MODELS_ARRAY_FIELD, Arrays.asList(Constants.AI_VALUE));
        map.put(SimilarPatentServer.CHART_MODELS_ARRAY_FIELD, Arrays.asList(Constants.PIE_CHART, Arrays.asList(Constants.HISTOGRAM)));
        map.put(Constants.HISTOGRAM,Constants.AI_VALUE);
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD,"");
        return map;
    }
}
