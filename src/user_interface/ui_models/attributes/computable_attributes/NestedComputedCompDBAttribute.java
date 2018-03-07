package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 12/8/17.
 */
public class NestedComputedCompDBAttribute extends ComputableAttribute<List<Map<String,Object>>> {
    private static Map<String,List<Map<String,Object>>> assetToCompDBDataMap;
    public NestedComputedCompDBAttribute() {
        super(Collections.emptyList());
        if(assetToCompDBDataMap==null) {
            assetToCompDBDataMap = Database.getCompDBAssetToNestedDataMap();
        }
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }

    @Override
    public String getName() {
        return Constants.COMPDB;
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }


    @Override
    public List<Map<String,Object>> attributesFor(Collection<String> portfolio, int limit, Boolean isApp) {
        String item = portfolio.stream().findAny().get();
        return assetToCompDBDataMap.get(item);
    }


    @Override // don't want to update while ingesting
    public List<Map<String,Object>> handleIncomingData(String item, Map<String, Object> data, Map<String,List<Map<String,Object>>> myData, boolean isApplication) {
        return null;
    }
}
