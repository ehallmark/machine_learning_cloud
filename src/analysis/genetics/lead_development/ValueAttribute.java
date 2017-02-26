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

    private ValueAttribute(String name, double importance, Evaluator model, int id) {
        super(name,importance,id);
        this.model=model;
    }

    @Override
    public Attribute dup() {
        return new ValueAttribute(name,importance,model,getId());
    }

    @Override
    public double scoreAssignee(String assignee) {
        return model.evaluate(assignee);
    }
}
