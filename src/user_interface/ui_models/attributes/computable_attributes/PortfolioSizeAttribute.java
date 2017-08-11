package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class PortfolioSizeAttribute extends ComputableAttribute<Integer> {
    public PortfolioSizeAttribute() {
        // TODO get nested computable attributes to work
        //super(Arrays.asList(AbstractFilter.FilterType.Between));
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return Constants.PORTFOLIO_SIZE;
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

}

