package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class CPCNestedAttribute extends NestedAttribute {

    public CPCNestedAttribute() {
        super(Arrays.asList(new CPCAttribute(), new CPCTitleAttribute(), new CPCSectionAttribute(), new CPCClassAttribute(), new CPCSubclassAttribute(), new CPCMainGroupAttribute(), new CPCSubgroupAttribute()));
    }

    @Override
    public String getName() {
        return Constants.NESTED_CPC_CODES;
    }

    @Override
    public boolean isNotYetImplemented() {
        return true;
    }

}
