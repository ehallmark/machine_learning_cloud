package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/15/17.
 */
public class PortfolioSizeAttribute extends ComputableAttribute<Integer> {
    public PortfolioSizeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }


    @Override
    public Integer handleIncomingData(String name, Map<String, Object> data, boolean isApplication) {
        return null;
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

