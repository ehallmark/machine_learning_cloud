package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractExcludeFilter extends AbstractFilter {
    protected Collection<String> labels;
    protected FieldType fieldType;
    protected String nestedField;
    public AbstractExcludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType, FieldType fieldType, Collection<String> labels, String nestedField) {
        super(attribute,filterType);
        this.fieldType=fieldType;
        this.labels = labels;
        this.nestedField=nestedField;
    }

    public AbstractExcludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        this(attribute,filterType,fieldType,labels,null);
    }


    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        if(attribute.getType().equals("nested") && nestedField!=null) {
            builder=builder.mustNot(QueryBuilders.matchQuery(getPrerequisite()+"."+nestedField,labels));
        } else if(attribute.getType().equals("text")) {
            builder=builder.mustNot(QueryBuilders.matchQuery(getPrerequisite(),labels));
        } else {
            builder=builder.mustNot(QueryBuilders.termsQuery(getPrerequisite(),labels));
        }
        return builder;
    }

    public boolean isActive() { return labels!=null && labels.size() > 0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        if (fieldType.equals(FieldType.Text)) {
            labels = preProcess(extractString(req, getName(), "").toUpperCase(), "\n", "[^a-zA-Z0-9 \\/]");
        } else {
            labels = SimilarPatentServer.extractArray(req, getName());
        }
    }

    @Override
    public Tag getOptionsTag() {
        if (fieldType.equals(FieldType.Text)) {
            return div().with(
                    textarea().withClass("form-control").attr("placeholder","1 per line.").withName(getName())
            );
        } else {
            return div().with(
                    SimilarPatentServer.technologySelect(getPrerequisite(), getAllValues())
            );
        }
    }
}
