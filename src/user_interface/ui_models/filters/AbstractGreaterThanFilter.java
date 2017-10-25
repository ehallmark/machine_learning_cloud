package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;
import static j2html.TagCreator.label;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractGreaterThanFilter extends AbstractFilter {
    @Getter
    protected Object limit;

    public AbstractGreaterThanFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractGreaterThanFilter(attribute,filterType);
    }


    @Override
    public QueryBuilder getFilterQuery() {
        if(limit == null) {
            return QueryBuilders.boolQuery();
        } else {
            if(isScriptFilter) {
                return getScriptFilter();
            } else {
                return QueryBuilders.rangeQuery(getFullPrerequisite())
                        .gte(limit);
            }
        }
    }

    @Override
    protected String transformAttributeScript(String script) {
        return "("+script+") >= "+limit;
    }

    public boolean isActive() { return limit!=null; }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        if(getFieldType().equals(FieldType.Date)) {
            this.limit = SimilarPatentServer.extractString(params, getName(), null);
            if(limit != null) {
                limit = LocalDate.parse(limit.toString()).format(DateTimeFormatter.ISO_DATE);
            }
        } else {
            this.limit = SimilarPatentServer.extractDoubleFromArrayField(params, getName(), null);
        }
        System.out.println("Filter "+getName()+": greater than "+limit);
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        String type = getFieldType().equals(FieldType.Date) ? "text" : "number";
        return div().with(
                input().withClass("form-control").withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()).withType(type).withName(getName())
        );
    }
}
