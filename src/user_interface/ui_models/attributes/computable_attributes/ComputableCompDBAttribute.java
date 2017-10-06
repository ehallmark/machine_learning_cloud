package user_interface.ui_models.attributes.computable_attributes;

import lombok.Getter;
import lombok.NonNull;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/15/17.
 */
public abstract class ComputableCompDBAttribute extends ComputableAttribute<List<Map<String,Object>>> {
    private static Map<String,List<Map<String,Object>>> compDBNestedData;

    public static Map<String,List<Map<String,Object>>> getCompDBNestedData() {
        if(compDBNestedData==null) {
            compDBNestedData = Database.getCompDBAssetToNestedDataMap();
        }
        return compDBNestedData;
    }
    public ComputableCompDBAttribute() {
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<Map<String,Object>> attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        return getCompDBNestedData().get(item);
    }
}

