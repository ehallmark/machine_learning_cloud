package user_interface.ui_models.filters;

import elasticsearch.DataIngester;
import j2html.tags.Tag;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import spark.Request;
import user_interface.acclaim_compatibility.Parser;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AcclaimExpertSearchFilter extends AbstractFilter {
    public static final String NAME = "acclaim_expert_filter";
    protected String mainQueryStr;
    protected String filingQueryStr;

    public AcclaimExpertSearchFilter() {
        super(new AcclaimAttribute(),FilterType.AdvancedKeyword);
    }

    @Override
    public AbstractFilter dup() {
        return new AcclaimExpertSearchFilter();
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if((mainQueryStr==null||mainQueryStr.length()==0)&&(filingQueryStr==null||filingQueryStr.length()==0)) {
            return QueryBuilders.boolQuery();
        } else {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            if(mainQueryStr!=null&&mainQueryStr.length()>0) {
                QueryBuilder query = QueryBuilders.queryStringQuery(mainQueryStr)
                        .defaultOperator(Operator.AND)
                        .analyzeWildcard(true);
                boolQueryBuilder = boolQueryBuilder.must(query);
            }
            if(filingQueryStr!=null&&filingQueryStr.length()>0) {
                QueryBuilder query = QueryBuilders.queryStringQuery(filingQueryStr)
                        .defaultOperator(Operator.AND)
                        .analyzeWildcard(true);
                HasParentQueryBuilder filingQuery = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,query,false);
                boolQueryBuilder = boolQueryBuilder.must(filingQuery);
            }
            System.out.println("Acclaim query: "+boolQueryBuilder.toString());

            return QueryBuilders.boolQuery().filter(boolQueryBuilder);
        }
    }


    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Filter not supported by scripts");
    }

    public boolean isActive() {return (mainQueryStr!=null && mainQueryStr.length()>0) || (filingQueryStr!=null && filingQueryStr.length()>0); }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        String queryStr = String.join("", SimilarPatentServer.extractArray(req,getName()));
        Parser parser = new Parser();
        Pair<String,String> p = parser.parseAcclaimQuery(queryStr);
        filingQueryStr=p.getFirst();
        mainQueryStr=p.getSecond();
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

class AcclaimAttribute extends AbstractAttribute {
    public AcclaimAttribute() {
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return AcclaimExpertSearchFilter.NAME;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }
}
