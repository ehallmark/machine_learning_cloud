package analysis.genetics.lead_development;

import value_estimation.Evaluator;

/**
 * Created by Evan on 2/25/2017.
 */
public class ValueAttribute extends Attribute {
    protected Evaluator model;
    public ValueAttribute(String name, double importance, Evaluator model) {
        super(name,importance);
        this.model=model;
    }

    @Override
    public double scoreAssignee(String assignee) {
        return model.evaluate(assignee);
    }
}
