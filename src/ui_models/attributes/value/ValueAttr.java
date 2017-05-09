package ui_models.attributes.value;

import seeding.Database;
import ui_models.attributes.AbstractAttribute;
import ui_models.portfolios.AbstractPortfolio;
import ui_models.attributes.value.ValueMapNormalizer;
import ui_models.portfolios.PortfolioList;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ValueAttr implements AbstractAttribute<Double> {
    // Instance class
    protected Map<String,Double> model;
    protected String modelName;
    protected ValueMapNormalizer.DistributionType distributionType;

    public ValueAttr(ValueMapNormalizer.DistributionType distributionType, String modelName) {
        this.modelName=modelName;
        this.distributionType=distributionType;
    }

    public void setModel() {
        model=new ValueMapNormalizer(distributionType).normalizeAndMergeModels(loadModels());
    }

    public String getModelName() {
        return modelName;
    }


    protected abstract List<Map<String,Double>> loadModels();

    public Map<String,Double> getMap() {
        return model;
    }

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(AbstractPortfolio portfolio, int n) {
        return portfolio.getTokens().stream().collect(Collectors.averagingDouble(token->{
            if(model.containsKey(token)) {
                return model.get(token);
            } else {
                return ValueMapNormalizer.DEFAULT_START;
            }
        }));
    }

    public double evaluate(String token) {
        if(Database.isAssignee(token)) {
            return attributesFor(PortfolioList.asList(token,PortfolioList.Type.assignees),1);
        } else {
            return attributesFor(PortfolioList.asList(token, PortfolioList.Type.patents),1);
        }
    }
}
