package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractGreaterThanFilter extends AbstractFilter {
    @Getter
    protected Object limit;

    public AbstractGreaterThanFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        this(attribute,filterType,null);
    }

    public AbstractGreaterThanFilter(@NonNull AbstractAttribute attribute, FilterType filterType, Object limit) {
        super(attribute,filterType);
        this.limit=limit;
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
            this.limit = SimilarPatentServer.extractArray(params, getName()).stream().findFirst().orElse(null);
            if(limit != null && limit.toString().length()>0) {
                try {
                    limit = LocalDate.parse(limit.toString()).format(DateTimeFormatter.ISO_DATE);
                } catch(Exception e) {
                    throw new RuntimeException("Error parsing date: "+limit);
                }
            } else {
                limit = null;
            }
        } else {
            this.limit = SimilarPatentServer.extractDoubleFromArrayField(params, getName(), null);
        }
        System.out.println("Filter "+getName()+": greater than "+limit);
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        String type = getFieldType().equals(FieldType.Date) ? "text" : "number";
        String additionalClasses = getFieldType().equals(FieldType.Date) ? "datepicker" : "";
        return div().with(
                input().withClass("form-control "+additionalClasses).withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()).withType(type).withName(getName())
        );
    }
}
