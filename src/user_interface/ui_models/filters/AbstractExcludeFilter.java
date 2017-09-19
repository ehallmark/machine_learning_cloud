package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractExcludeFilter extends AbstractIncludeFilter {
    public AbstractExcludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, List<String> labels) {
        super(attribute,filterType,fieldType,labels);
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractExcludeFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("Exclude Filter not supported by scripts");
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

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if(termQuery) {
            boolQueryBuilder = boolQueryBuilder.mustNot(QueryBuilders.termsQuery(preReq, labels));
        } else {
            for (String label : labels) {
                boolQueryBuilder = boolQueryBuilder.mustNot(QueryBuilders.matchPhraseQuery(preReq, label));
            }
        }
        return boolQueryBuilder;
    }

}
