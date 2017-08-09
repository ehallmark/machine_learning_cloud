package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import tools.ClassCodeHandler;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractIncludeFilter extends AbstractFilter {
    protected Collection<String> labels;
    protected FieldType fieldType;

    public AbstractIncludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType);
        this.fieldType=fieldType;
        this.labels = labels;
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(attribute.getType().equals("text")) {
            return QueryBuilders.matchQuery(getPrerequisite(),labels);

        } else {
            return QueryBuilders.termsQuery(getPrerequisite(),labels);
        }
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
                    SimilarPatentServer.technologySelect(getName(), labels)
            );
        }
    }
}
