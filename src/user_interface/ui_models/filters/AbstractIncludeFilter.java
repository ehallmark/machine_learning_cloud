package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import tools.ClassCodeHandler;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractIncludeFilter extends AbstractFilter {
    @Getter @Setter
    protected Collection<String> labels;
    protected FieldType fieldType;

    public AbstractIncludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType);
        this.fieldType=fieldType;
        this.labels = labels;
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractIncludeFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Include Filter not supported by scripts");
    }

    @Override
    public QueryBuilder getFilterQuery() {
        final String preReq;
        final boolean termQuery;
        if(!attribute.getType().equals("keyword")) {
            if (fieldType.equals(FieldType.Multiselect)&&attribute.getNestedFields() != null) {
                preReq = getFullPrerequisite()+".raw";
                termQuery = true;
            } else {
                preReq = getFullPrerequisite();
                termQuery = false;
            }
        } else {
            preReq = getFullPrerequisite();
            termQuery = true;
        }

        if(termQuery) {
            return QueryBuilders.termsQuery(preReq, labels);
        } else {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            for (String label : labels) {
                boolQueryBuilder = boolQueryBuilder.should(QueryBuilders.matchPhraseQuery(preReq, label));
            }
            return boolQueryBuilder;
        }
    }

    public boolean isActive() { return labels!=null && labels.size() > 0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        if (!fieldType.equals(FieldType.Multiselect)) {
            System.out.println("Params for "+getName()+": "+String.join("",SimilarPatentServer.extractArray(req, getName())));
            labels = preProcess(String.join("",SimilarPatentServer.extractArray(req, getName())), "\n", null);
            System.out.println("Should include labels for "+getName()+": "+String.join(", ",labels));
        } else {
            labels = SimilarPatentServer.extractArray(req, getName());
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        if (!fieldType.equals(FieldType.Multiselect)) {
            return div().with(
                    textarea().attr("data-attribute",attribute.getName()).attr("data-filtertype",filterType.toString()).withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()).withClass("form-control").attr("placeholder","1 per line.").withName(getName())
            );
        } else {
            if(attribute instanceof AjaxMultiselect) {
                return div().with(
                        ajaxMultiSelect(getName(), ((AjaxMultiselect) attribute).ajaxUrl())
                );
            } else {
                return div().with(
                        SimilarPatentServer.technologySelect(getName(), getAllValues())
                );
            }
        }
    }

    public Tag ajaxMultiSelect(String name, String url) {
        return select().attr("style","width:100%;").withName(name).withId(("multiselect-multiselect-"+name).replaceAll("[\\[\\] ]","")).withClass("multiselect-ajax").attr("data-url",url).attr("multiple","multiple");
    }
}
