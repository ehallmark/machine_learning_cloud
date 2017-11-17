package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 6/15/17.
 */
public class IsGTTReservoirAttribute extends ComputableAssigneeAttribute<Boolean> {
    private static final List<String> reservoirAssignees = Arrays.asList(
            "VERIZON",
            "CELLCO PARTNERSHIP",
            "ATT ",
            "AT&T",
            "BELL TELEPHONE",
            "BELLSOUTH",
            "BELL-SOUTH",
            "BELL SOUTH",
            "ORANGE",
            "ORANGEFRANCE",
            "ORANGE-FRANCE",
            "ORANGE FRANCE",
            "SONY",
            "SWISSCOM",
            "RICOH",
            "KIMBERLYCLARK",
            "KIMBERLY CLARK",
            "KIMBERLY-CLARK",
            "TELIA",
            "PANASONIC",
            "MATSUSHITA",
            "INNOLUX"
    );

    public IsGTTReservoirAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.BoolTrue));
    }

    @Override
    protected Boolean attributesForAssigneeHelper(@NonNull String assignee) {
        if(assignee==null) return false;
        return reservoirAssignees.stream().anyMatch(reservoir->assignee.startsWith(reservoir));
    }

    @Override
    public String getName() {
        return Constants.GTT_RESERVOIR;
    }

    @Override
    public String getType() {
        return "boolean";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

}

