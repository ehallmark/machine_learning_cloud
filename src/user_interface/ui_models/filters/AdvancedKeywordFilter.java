package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Map;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AdvancedKeywordFilter extends AbstractFilter {
    public static final String OR = "|";
    public static final String AND = "+";
    public static final String NOT = "-";
    public static final String OR_FRIENDLY = "OR";
    public static final String AND_FRIENDLY = "AND";
    public static final String NOT_FRIENDLY = "NOT";

    @Setter
    protected String queryStr;

    public AdvancedKeywordFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }


    @Override
    public AbstractFilter dup() {
        return new AdvancedKeywordFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.simpleQueryStringQuery(queryStr)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(true) // might be slow but Scott Hicks uses it...
                    .field(getFullPrerequisite());
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
        // check for boolean operators
        queryStr = queryStr.replace(" "+OR_FRIENDLY+" ",OR);
        queryStr = queryStr.replace(" "+AND_FRIENDLY+" ",AND);
        queryStr = queryStr.replace(" "+NOT_FRIENDLY+" ",NOT);
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        return div().with(
                button("Syntax").withClass("miniTip btn btn-sm btn-outline-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Example: (\"find this phrase\" | \"or this one\")").withName(getName())
        );
    }


    @Override
    public boolean contributesToScore() { return Constants.SIMILARITY_ATTRIBUTE_CLASSES.stream().anyMatch(clazz->clazz.isInstance(attribute)); }
}
