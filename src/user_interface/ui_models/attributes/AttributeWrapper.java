package user_interface.ui_models.attributes;

import lombok.Getter;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Evan on 9/8/2017.
 */
public class AttributeWrapper extends AbstractAttribute {
    @Getter
    protected String name;
    @Getter
    protected Collection<AbstractAttribute> attributes;
    public AttributeWrapper(String name, Collection<AbstractAttribute> attributes) {
        super(Collections.emptyList());
        this.name=name;
        this.attributes=attributes;
    }

    @Override
    public String getType() {
        throw new UnsupportedOperationException("getType not supported");
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }
}
