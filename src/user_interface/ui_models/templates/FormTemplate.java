package user_interface.ui_models.templates;

import com.google.gson.Gson;
import lombok.Getter;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.*;

/**
 * Created by Evan on 6/24/2017.
 */
public class FormTemplate {
    @Getter
    protected String name;
    protected Map<String,Object> params;
    protected Map<String,Object> searchOptions;
    protected List<String> special;
    public FormTemplate(String name, Map<String,Object> params, Map<String,Object> searchOptions) {
        this.params=params;
        this.searchOptions=searchOptions;
        this.name=name;
        this.special=new ArrayList<>(params.keySet());
    }

    public String getHref() {
        return "javascript:resetSearchForm();applyParams("+new Gson().toJson(params)+","+new Gson().toJson(searchOptions)+","+new Gson().toJson(special)+");";
    }

    public static Map<String,Object> defaultOptions() {
        Map<String,Object> map = new HashMap<>();
        map.put(SimilarPatentServer.LIMIT_FIELD,100);
        map.put(SimilarPatentServer.COMPARATOR_FIELD, Constants.OVERALL_SCORE);
        map.put(SimilarPatentServer.SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        map.put(SimilarPatentServer.SORT_DIRECTION_FIELD, "desc");
        return map;
    }

    public List<FormTemplate> nestedForms() {
        return Collections.emptyList();
    }

}
