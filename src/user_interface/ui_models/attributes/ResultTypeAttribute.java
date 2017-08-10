package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class ResultTypeAttribute extends AbstractAttribute<String> {
    public ResultTypeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include));
    }

    @Override
    public String getName() {
        return Constants.DOC_TYPE;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public Collection<String> getAllValues() {
        return Arrays.stream(PortfolioList.Type.values()).map(type->type.toString()).collect(Collectors.toList());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
