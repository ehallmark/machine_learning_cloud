package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;
import static j2html.TagCreator.label;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractBetweenFilter extends AbstractFilter {
    protected Number max;
    protected Number min;
    protected String minName;
    protected String maxName;
    public AbstractBetweenFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
        this.minName = attribute.getName()+"_min"+ Constants.FILTER_SUFFIX;
        this.maxName = attribute.getName()+"_max"+ Constants.FILTER_SUFFIX;
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(min == null || max == null) {
            return QueryBuilders.boolQuery();
        } else {
            if (isScriptFilter) {
                return getScriptFilter();
            } else {
                return QueryBuilders.rangeQuery(getFullPrerequisite())
                        .gt(min)
                        .lt(max);
            }
        }
    }

    @Override
    protected String transformAttributeScript(String script) {
        return "(("+script+") > "+min+") && (("+script+") < "+max+")";
    }

    public boolean isActive() { return min!=null&&max!=null && max.doubleValue()>min.doubleValue();  }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        this.min = SimilarPatentServer.extractDoubleFromArrayField(params,minName,null);
        this.max = SimilarPatentServer.extractDoubleFromArrayField(params,maxName,null);

        System.out.println(("Filter "+getName()+": between "+min)+ " and "+max);

    }

    @Override
    public Tag getOptionsTag() {
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Min"),
                        input().withClass("form-control").withType("number").withValue("0").withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()+minName.replaceAll("[\\[\\]]","")).withName(minName)
                ), div().withClass("col-6").with(
                        label("Max"),
                        input().withClass("form-control").withType("number").withValue("0").withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()+maxName.replaceAll("[\\[\\]]","")).withName(maxName)
                )
        );
    }
}
