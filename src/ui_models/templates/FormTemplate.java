package ui_models.templates;

import com.google.gson.Gson;
import lombok.Getter;
import seeding.Constants;
import server.SimilarPatentServer;
import ui_models.portfolios.PortfolioList;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class FormTemplate {
    @Getter
    protected String name;
    protected Map<String,Object> params;
    protected Map<String,Object> searchOptions;
    public FormTemplate(String name, Map<String,Object> params, Map<String,Object> searchOptions) {
        this.params=params;
        this.searchOptions=searchOptions;
        this.name=name;
    }

    public String getHref() {
        return "javascript:resetSearchForm();setTimeout(applyParams("+new Gson().toJson(params)+","+new Gson().toJson(searchOptions)+",200);";
    }


    public static Map<String,Object> similarityAssigneeSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,10);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.assignees.toString());
        return map;
    }

    public static Map<String,Object> similarityPatentSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,10);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        return map;
    }

    public static Map<String,Object> valueAssigneeSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,10);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.assignees.toString());
        return map;
    }

    public static Map<String,Object> valuePatentSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,10);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        return map;
    }
}
