package ui_models.templates;

import com.google.gson.Gson;
import lombok.Getter;

import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class FormTemplate {
    @Getter
    protected String name;
    protected Map<String,Object> params;
    public FormTemplate(String name, Map<String,Object> params) {
        this.params=params;
        this.name=name;
    }

    public String getHref() {
        return "javascript:resetSearchForm();setTimeout(applyParams("+new Gson().toJson(params)+"),200);";
    }
}
