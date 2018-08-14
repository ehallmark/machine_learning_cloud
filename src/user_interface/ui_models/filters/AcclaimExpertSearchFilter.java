package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.acclaim_compatibility.GlobalParser;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 7/9/2017.
 */
public class AcclaimExpertSearchFilter extends AbstractFilter {
    public static final String NAME = "acclaim_expert_filter";
    @Setter
    protected QueryBuilder query;
    protected Boolean includeSynonyms;
    protected Double minSimilarity;
    protected Integer maxSynonyms;

    public AcclaimExpertSearchFilter() {
        super(new AcclaimAttribute(),FilterType.AdvancedKeyword);
    }

    @Override
    public AbstractFilter dup() {
        return new AcclaimExpertSearchFilter();
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
        includeSynonyms = SimilarPatentServer.extractBool(req, getUseSynonymsId());
        maxSynonyms = SimilarPatentServer.extractInt(req, Constants.MAX_SYNONYMS_FIELD_ID, Constants.DEFAULT_MAX_SYNONYMS);
        minSimilarity = BigQueryServer.extractDouble(req, Constants.MIN_SIMILARITY_FIELD_ID, Constants.DEFAULT_MIN_SIMILARITY);
        if(includeSynonyms && (minSimilarity==null || minSimilarity<0 || minSimilarity>1)) {
            throw new RuntimeException("Minimum similarity for synonyms must be between 0-1. Set to 0 to disable synonyms.");
        }
        if(includeSynonyms && (maxSynonyms==null || maxSynonyms < 0)) {
            throw new RuntimeException("Maximum synonyms must be non-negative. Set to 0 to disable synonyms.");
        }
        if(maxSynonyms==null) maxSynonyms = Constants.DEFAULT_MAX_SYNONYMS;
        if(minSimilarity==null) minSimilarity = Constants.DEFAULT_MIN_SIMILARITY;
        if(minSimilarity==0||maxSynonyms==0) {
            includeSynonyms=false;
        }
        if (user != null) {
            String userGroup = BigQueryServer.getUserGroupFor(req.session());
            GlobalParser parser = new GlobalParser(user, userGroup, includeSynonyms, maxSynonyms, minSimilarity);
            query = parser.parseAcclaimQuery(queryStr);
            this.synonymMap = parser.getSynonymMap();
        }
    }

    @Override
    public List<String> getInputIds() {
        List<String> list = new ArrayList<>(super.getInputIds());
        list.add(getUseSynonymsId());
        return list;
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        return div().with(
                div().withText("Syntax").withClass("miniTip2 btn btn-sm btn-outline-secondary"),
                textarea().withId(getId()).withClass("form-control").attr("placeholder","Example: (ACLM:\"find in claim text\"^3 OR TTL:\"in invention title\"~6) || ACLM:prefi* || TTL:wil?card && NOT EXP:expired AND EXP:[NOW+5years TO *]").withName(getName()),
                br(),
                label("Synonym Search? ").attr("title", "Allows synonyms of keywords to be matched.").with(
                        input().withType("checkbox").withId(getUseSynonymsId()).withName(getUseSynonymsId()).withValue("on")
                )
        );
    }

    public String getUseSynonymsId() {
        return getId()+"_use_synonyms";
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
