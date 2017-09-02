package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractPrefixIncludeFilter extends AbstractIncludeFilter {
    public AbstractPrefixIncludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, List<String> labels) {
        super(attribute, filterType, fieldType, labels);
    }


    @Override
    public AbstractFilter dup() {
        return new AbstractPrefixIncludeFilter(attribute,filterType,fieldType,labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder query = QueryBuilders.boolQuery();
        for(String label : labels) {
            query = query.should(QueryBuilders.prefixQuery(getFullPrerequisite(), label));
        }
        return query;
    }

}
