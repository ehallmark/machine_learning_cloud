package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Setter;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.acclaim_compatibility.Parser;
import user_interface.server.SimilarPatentServer;

import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AcclaimExpertSearchFilter extends AbstractFilter {
    public static final String NAME = "acclaim_expert_filter";
    @Setter
    protected String queryStr;

    public AcclaimExpertSearchFilter() {
        super(null,FilterType.AdvancedKeyword);
    }

    @Override
    public AbstractFilter dup() {
        return new AcclaimExpertSearchFilter();
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.queryStringQuery(queryStr)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(true);
        }
    }


    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Filter not supported by scripts");
    }

    public boolean isActive() {return queryStr!=null && queryStr.length()>0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = String.join("", SimilarPatentServer.extractArray(req,getName()));
        Parser parser = new Parser();
        queryStr = parser.parseAcclaimQuery();
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                button("Syntax").withClass("miniTip2 btn btn-sm btn-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Example: (ACLM:\"find in claim text\" OR TTL:\"in invention title\")").withName(getName())
        );
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getFullPrerequisite() {
        return NAME;
    }

    @Override
    public String getFullName() {
        return NAME;
    }

    @Override
    public String getPrerequisite() {
        return NAME;
    }

    @Override
    public String getType() {
        return "text";
    }
}
