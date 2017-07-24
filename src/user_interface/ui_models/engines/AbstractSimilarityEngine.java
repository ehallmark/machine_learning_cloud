package user_interface.ui_models.engines;

import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.ValueAttr;
import models.value_models.ValueMapNormalizer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;

import static j2html.TagCreator.label;

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
        secondFinder = finderPrototype.duplicateWithScopeFromLabels(inputsToSearchFor);
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
