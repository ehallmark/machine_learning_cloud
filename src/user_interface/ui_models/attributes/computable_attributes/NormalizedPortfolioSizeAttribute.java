package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.NormalizedAssigneeToAssetsMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NormalizedPortfolioSizeAttribute extends ComputableAssigneeAttribute<Integer> {
    private static NormalizedAssigneeToAssetsMap assigneeToAssetsMap;

    public static AssigneeToAssetsMap getAssigneeToAssetsMap() {
        if(assigneeToAssetsMap==null) {
            assigneeToAssetsMap = new NormalizedAssigneeToAssetsMap();
        }
        return assigneeToAssetsMap;
    }

    public NormalizedPortfolioSizeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    protected Integer attributesForAssigneeHelper(@NonNull String assignee) {
        Integer portfolioSize = Math.max(getAssigneeToAssetsMap().getPatentDataMap().getOrDefault(assignee,Collections.emptyList()).size(),getAssigneeToAssetsMap().getApplicationDataMap().getOrDefault(assignee,Collections.emptyList()).size());
        return portfolioSize;
    }

    @Override
    public String getName() {
        return Constants.NORMALIZED_PORTFOLIO_SIZE;
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

