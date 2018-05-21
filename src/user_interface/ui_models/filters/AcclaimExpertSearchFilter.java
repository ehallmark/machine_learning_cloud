package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.acclaim_compatibility.GlobalParser;
import user_interface.acclaim_compatibility.USParser;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collections;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AcclaimExpertSearchFilter extends AbstractFilter {
    public static final String NAME = "acclaim_expert_filter";
    @Setter
    protected QueryBuilder query;

    private final boolean isBigQuery;
    public AcclaimExpertSearchFilter(boolean isBigQuery) {
        super(new AcclaimAttribute(),FilterType.AdvancedKeyword);
        this.isBigQuery=isBigQuery;
    }

    @Override
    public AbstractFilter dup() {
        return new AcclaimExpertSearchFilter(isBigQuery);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(query==null) {
            return QueryBuilders.boolQuery();
        } else {
            System.out.println("Acclaim query: "+query.toString());
            return query;
        }
    }


    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Filter not supported by scripts");
    }

    public boolean isActive() {return query!=null; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        String queryStr = String.join("", SimilarPatentServer.extractArray(req, getName()));
        String user = req.session(false).attribute("username");
        if (user != null) {
            if(isBigQuery) {
                GlobalParser parser = new GlobalParser(user);
                query = parser.parseAcclaimQuery(queryStr);
            } else {
                USParser parser = new USParser(user);
                query = parser.parseAcclaimQuery(queryStr);
            }
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                button("Syntax").withClass("miniTip2 btn btn-sm btn-outline-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Example: (ACLM:\"find in claim text\"^3 OR TTL:\"in invention title\"~6) || ACLM:prefi* || TTL:wil?card && NOT EXP:expired AND EXP:[NOW+5years TO *]").withName(getName())
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

    @Override
    public boolean contributesToScore() {
        return true;
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
