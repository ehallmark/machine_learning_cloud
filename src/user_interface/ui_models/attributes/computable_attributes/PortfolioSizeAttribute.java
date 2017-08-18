package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
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
    private AssetToAssigneeMap assetToAssigneeMap = new AssetToAssigneeMap();
    private AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
    public PortfolioSizeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        boolean probablyApplication = Database.isApplication(item);
        String assignee = probablyApplication ? assetToAssigneeMap.getApplicationDataMap().getOrDefault(item,assetToAssigneeMap.getPatentDataMap().get(item))
                : assetToAssigneeMap.getPatentDataMap().getOrDefault(item,assetToAssigneeMap.getApplicationDataMap().get(item));
        if(assignee == null) return null;
        return assigneeToAssetsMap.getPatentDataMap().getOrDefault(item,Collections.emptyList()).size();
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

