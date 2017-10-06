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
    @Getter
    protected String attributesMap;
    @Getter
    protected String filtersMap;
    @Getter
    protected String chartsMap;
    @Getter
    protected String searchOptionsMap;
    @Getter
    protected String highlightMap;
    public FormTemplate(String name, String searchOptionsMap, String attributesMap, String filtersMap, String chartsMap, String highlightMap) {
        this.name=name;
        this.searchOptionsMap=searchOptionsMap;
        this.attributesMap=attributesMap;
        this.filtersMap=filtersMap;
        this.chartsMap=chartsMap;
        this.highlightMap=highlightMap;
    }
}
