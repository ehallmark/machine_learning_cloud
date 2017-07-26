package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.AbstractSimilarityModel;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import models.classification_models.SimilarityGatherTechTagger;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TechnologySimilarityEngine extends AbstractSimilarityEngine {

    public TechnologySimilarityEngine(AbstractSimilarityModel model) {
        super(model);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        List<String> technologies = SimilarPatentServer.extractArray(req, SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD);
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        inputsToSearchFor.addAll(technologies.stream().filter(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().containsKey(technology)).flatMap(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().get(technology).stream()).collect(Collectors.toSet()));
        return inputsToSearchFor;
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD)
        );
    }
}
