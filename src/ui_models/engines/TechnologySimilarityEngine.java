package ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.attributes.classification.SimilarityGatherTechTagger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TechnologySimilarityEngine extends AbstractSimilarityEngine {

    public TechnologySimilarityEngine() {
        super(Constants.TECHNOLOGY_SIMILARITY);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        List<String> technologies = SimilarPatentServer.extractArray(req, SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD);
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        inputsToSearchFor.addAll(technologies.stream().filter(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().containsKey(technology)).flatMap(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().get(technology).stream()).collect(Collectors.toSet()));
        return inputsToSearchFor;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                label("Technology"),br(),
                SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD), br()
        );
    }
}
