package models.genetics.lead_development;

import models.value_models.ValueAttr;

/**
 * Created by Evan on 2/25/2017.
 */
public class ValueAttribute extends Attribute {
    protected ValueAttr model;
    public ValueAttribute(String name, double importance, ValueAttr model) {
        super(name,importance);
        this.model=model;
    }

    private ValueAttribute(String name, double importance, ValueAttr model, int id) {
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
