package seeding.google.elasticsearch.attributes;

import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.RangeAttribute;

import java.util.Arrays;
import java.util.List;

public class AIValue extends DoubleAttribute implements RangeAttribute {
    @Override
    public String getName() {
        return Attributes.AI_VALUE;
    }

    @Override
    public List<Pair<Number, Number>> getRanges() {
        return Arrays.asList(
                new Pair<>(0,20),
                new Pair<>(20,40),
                new Pair<>(40,60),
                new Pair<>(60,80),
                new Pair<>(80,100)
        );
    }

    @Override
    public Object missing() {
        return null;
    }

    @Override
    public String valueSuffix() {
        return "%";
    }
}
