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
public class AbstractExcludeFilter extends AbstractFilter {
    @Setter
    protected List<String> labels;
    protected FieldType fieldType;
    public AbstractExcludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, List<String> labels) {
        super(attribute,filterType);
        this.fieldType=fieldType;
        this.labels = labels;
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
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        if(!attribute.getType().equals("keyword")) {
            if (fieldType.equals(FieldType.Multiselect)&&attribute.getNestedFields() != null) {
                builder=builder.mustNot(QueryBuilders.termsQuery(getFullPrerequisite()+".raw", labels));
            }
            builder=builder.mustNot(QueryBuilders.matchPhraseQuery(getFullPrerequisite(),labels));
        } else {
            builder=builder.mustNot(QueryBuilders.termsQuery(getFullPrerequisite(),labels));
        }
        return builder;
    }

    public boolean isActive() { return labels!=null && labels.size() > 0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        if (fieldType.equals(FieldType.Text)) {
            labels = preProcess(String.join("",SimilarPatentServer.extractArray(req, getName())), "\n", null);
            System.out.println("Should remove labels for "+getName()+": "+String.join(", ",labels));
        } else {
            labels = SimilarPatentServer.extractArray(req, getName());
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        if (!fieldType.equals(FieldType.Multiselect)) {
            return div().with(
                    textarea().withId(getName().replaceAll("[\\[\\]]","")+filterType.toString()).withClass("form-control").attr("placeholder","1 per line.").withName(getName())
            );
        } else {
            return div().with(
                    SimilarPatentServer.technologySelect(getName(), getAllValues())
            );
        }
    }
}
