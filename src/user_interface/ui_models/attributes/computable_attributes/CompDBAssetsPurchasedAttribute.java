package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class CompDBAssetsPurchasedAttribute extends ComputableAssigneeAttribute<Integer> {
    private static Map<String,Integer> MODEL;

    @Override
    public String getName() {
        return Constants.COMPDB_ASSETS_PURCHASED;
    }

    public CompDBAssetsPurchasedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
        if(MODEL==null)MODEL=runModel();
    }


    private static Map<String,Integer> runModel(){
        System.out.println("Starting to load CompDBAssetsPurchasedAttribute...");
        Map<String,Integer> assigneeToAssetsPurchasedCountMap = Database.getCompDBAssigneeToAssetsPurchasedCountMap();
        return assigneeToAssetsPurchasedCountMap;
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

    @Override
    protected Integer attributesForAssigneeHelper(String assignee) {
        if(MODEL==null) return 0;
        return MODEL.getOrDefault(assignee, 0);
    }
}
