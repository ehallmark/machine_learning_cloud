package user_interface.ui_models.attributes;

import lombok.Getter;
import user_interface.ui_models.filters.*;

import java.util.Arrays;
import java.util.Collection;


/**
 * Created by Evan on 5/9/2017.
 */
public abstract class NestedAttribute<T> extends AbstractAttribute<T> {
    @Getter
    protected Collection<AbstractAttribute> attributes;

    public NestedAttribute(Collection<AbstractAttribute> attributes) {
        super(Arrays.asList(AbstractFilter.FilterType.Nested));
        this.attributes = attributes;
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }

}
