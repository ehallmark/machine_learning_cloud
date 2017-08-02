package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by ehallmark on 5/10/17.
 */
public class RemoveLabelFilter extends AbstractExcludeFilter {
    public RemoveLabelFilter() {super();}

    public RemoveLabelFilter(Collection<String> labelsToRemove) {
        super();
        this.labels=labelsToRemove;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent or application per line (eg. 800000)").withName(Constants.LABEL_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        labels = new HashSet<>(SimilarPatentServer.preProcess(SimilarPatentServer.extractString(req, Constants.LABEL_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).stream()
                .map(label->label.startsWith("US")?label.replaceFirst("US",""):label).collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return Constants.LABEL_FILTER;
    }


    @Override
    public String getPrerequisite() {
        return Constants.NAME;
    }


}
