package ui_models.portfolios.attributes;

import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.value.ValueMapNormalizer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 6/17/2017.
 */
public class DoNothingValueAttribute extends ValueAttr implements DoNothing {

    public DoNothingValueAttribute() {
        super(ValueMapNormalizer.DistributionType.None, null);
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return Collections.emptyList();
    }
}
