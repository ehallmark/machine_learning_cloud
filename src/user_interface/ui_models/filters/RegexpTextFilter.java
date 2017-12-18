package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class RegexpTextFilter extends AbstractFilter {
    @Setter
    protected String queryStr;

    public RegexpTextFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public AbstractFilter dup() {
        return new RegexpTextFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.regexpQuery(getFullPrerequisite(),queryStr)
                    .maxDeterminizedStates(20000);

        }
    }

    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Include Filter not supported by scripts");
    }

    public boolean isActive() {return queryStr!=null && queryStr.length()>0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = String.join("", SimilarPatentServer.extractArray(req,getName()));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                a("Syntax").withHref("https://www.elastic.co/guide/en/elasticsearch/reference/5.6/query-dsl-regexp-query.html#regexp-syntax").withClass("btn btn-sm btn-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Certain regular expressions may drastically reduce performance. Use with caution.").withName(getName())
        );
    }


    @Override
    public boolean contributesToScore() { return Constants.SIMILARITY_ATTRIBUTE_CLASSES.stream().anyMatch(clazz->clazz.isInstance(attribute)); }
}
