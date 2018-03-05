package graphical_modeling.model.learning.distributions;

import graphical_modeling.model.nodes.FactorNode;

/**
 * Created by Evan on 4/29/2017.
 */
public abstract class DistributionCreator {
    public abstract Distribution create(FactorNode factor);
}
