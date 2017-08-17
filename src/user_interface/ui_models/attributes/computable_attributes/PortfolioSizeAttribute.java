package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        Integer portfolioSize = portfolio.stream().flatMap(p->Database.assigneesFor(p).stream())
                .distinct().collect(Collectors.summingInt(a->Database.selectPatentNumbersFromExactAssignee(a).size()));
        if(portfolio==null) portfolioSize = 0;
        return portfolioSize;
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

