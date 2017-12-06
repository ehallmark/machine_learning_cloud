package user_interface.ui_models.templates;

import lombok.Getter;

import java.io.File;

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
    @Getter
    protected File file;
    @Getter
    protected String[] assets;
    public FormTemplate(File file, String name, String searchOptionsMap, String attributesMap, String filtersMap, String chartsMap, String highlightMap) {
        this.file=file;
        this.name=name;
        this.searchOptionsMap=searchOptionsMap;
        this.attributesMap=attributesMap;
        this.filtersMap=filtersMap;
        this.chartsMap=chartsMap;
        this.highlightMap=highlightMap;
    }

    public FormTemplate(File file, String name) {
        this.file=file;
        this.name=name;
    }

    public FormTemplate(File file, String name, String[] assets) {
        this.file=file;
        this.assets=assets;
        this.name=name;
    }
}
