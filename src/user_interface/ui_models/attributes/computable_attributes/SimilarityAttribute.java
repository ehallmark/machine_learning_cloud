package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 6/15/17.
 */
public class SimilarityAttribute extends ComputableAttribute<String> {

    public SimilarityAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.GreaterThan));
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        throw new UnsupportedOperationException("attributesFor is unsupported for similarity attribute.");
    }

    @Override
    public String getName() {
        return Constants.SIMILARITY;
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public boolean supportedByElasticSearch() {
        return false;
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
