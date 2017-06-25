package ui_models.templates;

import seeding.Constants;
import server.SimilarPatentServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class SimilarPatentSearch extends FormTemplate {

    public SimilarPatentSearch() {
        super(Constants.SIMILAR_PATENT_SEARCH, getParams(), FormTemplate.similarityPatentSmall());
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.VALUE_MODELS_ARRAY_FIELD, Arrays.asList(Constants.AI_VALUE));
        map.put(SimilarPatentServer.CHART_MODELS_ARRAY_FIELD, Arrays.asList(Constants.PIE_CHART, Arrays.asList(Constants.HISTOGRAM)));
        map.put(Constants.HISTOGRAM,Constants.SIMILARITY);
        map.put(Constants.PIE_CHART,Constants.ASSIGNEE);
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.NAME,Constants.ASSIGNEE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD,"");
        return map;
    }
}
