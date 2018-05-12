package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.table;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class PatentSimilarityEngine extends AbstractSimilarityEngine {

    @Deprecated
    public PatentSimilarityEngine() {
        super();
    }

    public PatentSimilarityEngine(String tableName) {
        super(tableName, Attributes.PUBLICATION_NUMBER_FULL);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
        patents = patents.stream().limit(1000).flatMap(patent-> Stream.of(assetToFilingMap.getPatentDataMap().getOrDefault(patent, patent),assetToFilingMap.getApplicationDataMap().getOrDefault(patent, patent))).distinct().collect(Collectors.toList());
        System.out.println("Found "+patents.size()+" patents...");
        return patents;
    }


    @Override
    public String getName() {
        return Constants.PATENT_SIMILARITY;
    }

    @Override
    public String getId() {
        return PATENTS_TO_SEARCH_FOR_FIELD;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent or application per line (eg. 800000)").withId(getId()).withName(getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        if(tableName!=null) {
            return new PatentSimilarityEngine(tableName);
        } else {
            return new PatentSimilarityEngine();
        }
    }
}
