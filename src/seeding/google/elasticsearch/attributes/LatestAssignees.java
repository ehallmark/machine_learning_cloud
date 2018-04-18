package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class LatestAssignees extends NestedAttribute {
    public LatestAssignees() {
        super(Arrays.asList(new LatestAssignee(), new LatestAssigneeDate(), new LatestSecurityInterest(), new LatestFirstAssignee(), new LatestPortfolioSize(), new LatestPortfolioSize(), new LatestEntityType(), new LatestFirstFilingDate(), new LatestLastFilingDate()),true);
        this.isObject=true;
    }

        @Override
    public String getName() {
        return Attributes.LATEST_ASSIGNEES;
    }
}
