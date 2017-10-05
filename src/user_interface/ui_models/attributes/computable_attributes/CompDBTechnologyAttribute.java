package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CompDBTechnologyAttribute extends ComputableCompDBAttribute<Collection<String>> {

    public CompDBTechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude,AbstractFilter.FilterType.AdvancedKeyword));
    }

    @Override
    public Collection<String> attributesFor(Collection<String> items, int limit) {
        return Database.getCompdbAssetToTechnologiesMap().get(items.stream().findAny().get());
    }

    @Override
    public String getName() {
        return Constants.COMPDB_TECHNOLOGY;
    }

    @Override
    public String getType() {
        return "text";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public Collection<String> getAllValues() {
        return Database.getCompDBTechnologyToAssetsMap().keySet();
    }

    @Override
    public Map<String,Object> getNestedFields() {
        Map<String,Object> fields = new HashMap<>();
        Map<String,String> rawType = new HashMap<>();
        rawType.put("type","keyword");
        fields.put("raw",rawType);
        return fields;
    }

}

