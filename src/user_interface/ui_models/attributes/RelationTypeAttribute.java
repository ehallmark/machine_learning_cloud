package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class RelationTypeAttribute extends AbstractAttribute<String> {
    public RelationTypeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String getName() {
        return Constants.RELATION_TYPE;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public Collection<String> getAllValues() {
        return Arrays.stream(Constants.RELATED_DOC_TYPES).sorted().collect(Collectors.toList());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
