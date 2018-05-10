package seeding.google.elasticsearch.attributes;

import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.List;

public abstract class IntegerAttribute extends AbstractAttribute implements RangeAttribute {
    public IntegerAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

    @Override
    public List<Pair<Number, Number>> getRanges() {
        return Arrays.asList(
                new Pair<>(0,50),
                new Pair<>(50,100),
                new Pair<>(100,500),
                new Pair<>(500,1000),
                new Pair<>(1000, null)
        );
    }
}
