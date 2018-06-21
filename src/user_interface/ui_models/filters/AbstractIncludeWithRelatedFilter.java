package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Database;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractIncludeWithRelatedFilter extends AbstractIncludeAssetFilter {
    public AbstractIncludeWithRelatedFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType, fieldType, labels);

    }

    @Override
    public AbstractFilter dup() {
        return new AbstractIncludeWithRelatedFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        super.extractRelevantInformationFromParams(req);
        if(labels!=null && assetField != null) {
            // expand
            labels = Database.familyIdsForAssets(new ArrayList<>(labels), assetField);
        }
    }

    @Override
    public List<String> getAdditionalIds() {
        return Collections.emptyList();
    }

    @Override
    public QueryBuilder getFilterQuery() {
        final String preReq = assetPrefix+Attributes.FAMILY_ID;
        System.out.println("Using preReq for related assets: "+preReq);

        if(minimumShouldMatch<=1 && !(attribute instanceof TermsLookupAttribute)) {
            return QueryBuilders.termsQuery(preReq,labels);
        } else {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().minimumShouldMatch(this.minimumShouldMatch);
            for (String label : labels) {
                if (attribute instanceof TermsLookupAttribute) {
                    throw new RuntimeException("Unable to use 'With Related Assets Filter' for attribute: "+getFullPrerequisite()+". Reason: Currently not supported.");
                } else {
                    boolQueryBuilder = boolQueryBuilder.should(QueryBuilders.termQuery(preReq, label));
                }
            }
            return boolQueryBuilder;
        }
    }
}
