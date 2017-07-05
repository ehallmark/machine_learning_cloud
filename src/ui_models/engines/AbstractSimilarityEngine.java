package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import spark.Request;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.value.ValueMapNormalizer;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.br;
import static j2html.TagCreator.label;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public abstract class AbstractSimilarityEngine extends ValueAttr {
    protected AbstractSimilarityModel secondFinder;

    public AbstractSimilarityEngine(String name) {
        super(ValueMapNormalizer.DistributionType.None, name);
    }


    protected abstract Collection<String> getInputsToSearchFor(Request req, PortfolioList.Type resultType);

    protected AbstractSimilarityModel setSecondFinder(AbstractSimilarityModel finderPrototype, Collection<String> inputsToSearchFor) {
        secondFinder = finderPrototype.duplicateWithScope(SimilarPatentServer.findItemsByName(inputsToSearchFor));
        return secondFinder;
    }

    public void extractRelevantInformationFromParams(Request req) {

    }

    @Override
    public double evaluate(String item) {
        if(secondFinder==null||secondFinder.numItems()==0) return ValueMapNormalizer.DEFAULT_START;
        return secondFinder.similarityTo(item) * 100d;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return Collections.emptyList();
    }

    @Override
    public Double attributesFor(Collection<String> portfolio, int limit) {
        return evaluate(portfolio.stream().findAny().get());
    }

}
