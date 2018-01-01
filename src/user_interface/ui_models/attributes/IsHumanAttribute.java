package user_interface.ui_models.attributes;

import models.assignee_normalization.human_name_prediction.HumanNamePredictionPipelineManager;
import lombok.NonNull;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAssigneeAttribute;
import user_interface.ui_models.attributes.computable_attributes.LatestExecutionDateAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class IsHumanAttribute extends ComputableAssigneeAttribute<Boolean> {
    private static final Map<String,Boolean> humanPredictionMap = HumanNamePredictionPipelineManager.loadPipelineManager().loadPredictions();

    private LatestExecutionDateAttribute latestExecutionDateAttribute = new LatestExecutionDateAttribute();
    public IsHumanAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.BoolTrue));
    }

    @Override
    public String getName() {
        return Constants.IS_HUMAN;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    @Override
    public Boolean attributesFor(Collection<String> portfolio, int limit, Boolean isApp) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        boolean probablyApplication = Database.isApplication(item);
        // check if latest execution date exists, otherwise no need to update
        boolean hasExecutionDate = latestExecutionDateAttribute.getPatentDataMap().getOrDefault(item, latestExecutionDateAttribute.getApplicationDataMap().get(item)) != null;
        if(hasExecutionDate) {
            // run name model
            String assignee = probablyApplication ? getAppToAssigneeMap().getOrDefault(item,getPatentToAssigneeMap().get(item))
                    : getPatentToAssigneeMap().getOrDefault(item,getAppToAssigneeMap().get(item));
            return attributesForAssigneeHelper(assignee);
        }
        return null;
    }

    @Override
    protected Boolean attributesForAssigneeHelper(@NonNull String assignee) {
        // check if latest execution date exists, otherwise no need to update
        return humanPredictionMap.get(assignee);
    }
}
