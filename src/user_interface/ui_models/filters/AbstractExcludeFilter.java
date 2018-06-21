package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;

import java.util.ArrayList;
import java.util.List;

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
        if(attribute instanceof TermsLookupAttribute) {
            TermsLookupAttribute termsLookupAttribute = (TermsLookupAttribute)attribute;
            for(String label : labels) {
                boolQueryBuilder = boolQueryBuilder.mustNot(QueryBuilders.termsLookupQuery(termsLookupAttribute.getTermsName(), new TermsLookup(termsLookupAttribute.getTermsIndex(), termsLookupAttribute.getTermsType(), label, termsLookupAttribute.getTermsPath())));
            }
        } else if(termQuery) {
            boolQueryBuilder = boolQueryBuilder.mustNot(QueryBuilders.termsQuery(preReq, labels));
        } else {
            for (String label : labels) {
                boolQueryBuilder = boolQueryBuilder.mustNot(QueryBuilders.matchPhraseQuery(preReq, label));
            }
        }
        return boolQueryBuilder;
    }

}
