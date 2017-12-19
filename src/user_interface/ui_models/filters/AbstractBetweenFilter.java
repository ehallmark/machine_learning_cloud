package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractBetweenFilter extends AbstractFilter {
    protected Object max;
    protected Object min;
    protected String minName;
    protected String maxName;
    public AbstractBetweenFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
        this.minName = attribute.getFullName().replace(".","_")+"_min"+ Constants.FILTER_SUFFIX;
        this.maxName = attribute.getFullName().replace(".","_")+"_max"+ Constants.FILTER_SUFFIX;
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractBetweenFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(min == null && max == null) {
            return QueryBuilders.boolQuery();
        } else {
            if (isScriptFilter) {
                return getScriptFilter();
            } else {
                RangeQueryBuilder query = QueryBuilders.rangeQuery(getFullPrerequisite());
                if(min!=null) {
                    query = query.gte(min);
                }
                if(max!=null) {
                    query = query.lt(max);
                }
                return query;
            }
        }
    }

    @Override
    public List<String> getInputIds() {
        return Arrays.asList(getMinId(),getMaxId());
    }

    @Override
    protected String transformAttributeScript(String script) {
        StringJoiner query = new StringJoiner(" && ");
        Object minTmp = min;
        Object maxTmp = max;
        if(getFieldType().equals(FieldType.Date)) {
            if(minTmp!=null) minTmp = LocalDate.parse(minTmp.toString(),DateTimeFormatter.ISO_DATE).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            if(maxTmp!=null) maxTmp = LocalDate.parse(maxTmp.toString(),DateTimeFormatter.ISO_DATE).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        if(minTmp!=null) {
            query.add("(("+script+") >= "+minTmp+")");
        }
        if(maxTmp!=null) {
            query.add("(("+script+") < "+maxTmp+")");
        }
        return query.toString();
    }

    public boolean isActive() { return min!=null||max!=null;  }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        if(getFieldType().equals(FieldType.Date)) {
            this.min = SimilarPatentServer.extractArray(params, minName).stream().findFirst().orElse(null);
            this.max = SimilarPatentServer.extractArray(params, maxName).stream().findFirst().orElse(null);
            if(min != null && min.toString().length()>0) {
                try {
                    min = LocalDate.parse(min.toString()).format(DateTimeFormatter.ISO_DATE);
                } catch(Exception e) {
                    throw new RuntimeException("Error parsing date: "+min);
                }
                System.out.println("Found start date: "+min);
            } else {
                min = null;
            }
            if(max != null && max.toString().length()>0) {
                try {
                    max = LocalDate.parse(max.toString()).format(DateTimeFormatter.ISO_DATE);
                } catch(Exception e) {
                    throw new RuntimeException("Error parsing date: "+max);
                }
                System.out.println("Found end date: "+max);
            } else {
                max = null;
            }
        } else {
            this.min = SimilarPatentServer.extractDoubleFromArrayField(params, minName, null);
            this.max = SimilarPatentServer.extractDoubleFromArrayField(params, maxName, null);
        }
        System.out.println(("Filter "+getName()+": between "+min)+ " and "+max);

    }

    private String getMaxId() {
        return getName().replaceAll("[\\[\\]]","")+filterType.toString()+maxName.replaceAll("[\\[\\]]","");
    }

    private String getMinId() {
        return getName().replaceAll("[\\[\\]]","")+filterType.toString()+minName.replaceAll("[\\[\\]]","");
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        String type = getFieldType().equals(FieldType.Date) ? "text" : "number";
        String additionalClasses = getFieldType().equals(FieldType.Date) ? "datepicker" : "";
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Min").attr("style","width: 100%;").with(
                                input().withClass("form-control "+additionalClasses).withType(type).withId(getMinId()).withName(minName)
                        )
                ), div().withClass("col-6").with(
                        label("Max").attr("style","width: 100%;").with(
                                input().withClass("form-control "+additionalClasses).withType(type).withId(getMaxId()).withName(maxName)
                        )
                )
        );
    }
}
