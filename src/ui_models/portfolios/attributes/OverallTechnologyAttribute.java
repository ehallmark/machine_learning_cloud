package ui_models.portfolios.attributes;

import seeding.Constants;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.value.SpecificTechnologyEvaluator;
import ui_models.attributes.value.ValueMapNormalizer;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class OverallTechnologyAttribute implements AbstractAttribute<Double> {
    private List<SpecificTechnologyEvaluator> evaluators;
    public OverallTechnologyAttribute(List<SpecificTechnologyEvaluator> evaluators) {
        this.evaluators=evaluators;
    }
    @Override
    public Double attributesFor(Collection<String> portfolio, int limit) {
        if(evaluators.size()==0) return ValueMapNormalizer.DEFAULT_START;
        return evaluators.stream().collect(Collectors.averagingDouble(evaluator->evaluator.evaluate(portfolio.stream().findAny().get())));
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY_RELEVANCE;
    }
}
