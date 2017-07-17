package user_interface.templates;

import com.google.gson.Gson;
import lombok.Getter;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class FormTemplate {
    @Getter
    protected String name;
    protected Map<String,Object> params;
    protected Map<String,Object> searchOptions;
    protected List<String> special;
    public FormTemplate(String name, Map<String,Object> params, Map<String,Object> searchOptions, List<String> special) {
        this.params=params;
        this.searchOptions=searchOptions;
        this.name=name;
        this.special = special;
    }

    public String getHref() {
        return "javascript:resetSearchForm();applyParams("+new Gson().toJson(params)+","+new Gson().toJson(searchOptions)+","+new Gson().toJson(special)+");";
    }


    public static Map<String,Object> similarityAssigneeSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.assignees.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> similarityPatentSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> similarityAssetSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.assets.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> similarityApplicationSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.SIMILARITY);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.applications.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> valueAssigneeSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.assignees.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> valuePatentSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> valueApplicationSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.applications.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public static Map<String,Object> valueAssetSmall() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.AI_VALUE);
        map.put(SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.applications.toString());
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        return map;
    }

    public List<FormTemplate> nestedForms() {
        return Collections.emptyList();
    }

}
