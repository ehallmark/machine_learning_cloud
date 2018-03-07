package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
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
public class PortfolioSizeAttribute extends ComputableAssigneeAttribute<Integer> {
    private static AssigneeToAssetsMap assigneeToAssetsMap;

    public static AssigneeToAssetsMap getAssigneeToAssetsMap() {
        if(assigneeToAssetsMap==null) {
            assigneeToAssetsMap = new AssigneeToAssetsMap();
        }
        return assigneeToAssetsMap;
    }

    public PortfolioSizeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    protected Integer attributesForAssigneeHelper(@NonNull String assignee) {
        Integer portfolioSize = Math.max(getAssigneeToAssetsMap().getPatentDataMap().getOrDefault(assignee,Collections.emptyList()).size(),getAssigneeToAssetsMap().getApplicationDataMap().getOrDefault(assignee,Collections.emptyList()).size());
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

