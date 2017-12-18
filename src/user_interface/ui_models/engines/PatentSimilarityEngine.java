package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class PatentSimilarityEngine extends AbstractSimilarityEngine {

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
        patents = patents.stream().filter(patent->assetToFilingMap.getPatentDataMap().containsKey(patent)||assetToFilingMap.getApplicationDataMap().containsKey(patent)).collect(Collectors.toList());
        System.out.println("Found "+patents.size()+" patents...");
        return patents;
    }

    @Override
    public List<String> getInputIds() {
        return Collections.singletonList(PATENTS_TO_SEARCH_FOR_FIELD);
    }

    @Override
    public String getName() {
        return Constants.PATENT_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent or application per line (eg. 800000)").withId(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD).withName(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD)
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new PatentSimilarityEngine();
    }
}
