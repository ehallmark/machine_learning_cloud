package ui_models.portfolios.attributes;

import ui_models.attributes.AbstractAttribute;

import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public class DoNothingAttribute implements AbstractAttribute, DoNothing {
    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}
