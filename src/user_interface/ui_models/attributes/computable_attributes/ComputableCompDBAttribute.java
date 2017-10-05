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
public abstract class ComputableCompDBAttribute<T> extends ComputableAttribute<Collection<T>> {
    private static Map<String,List<Map<String,Object>>> compDBNestedData;

    public static Map<String,List<Map<String,Object>>> getCompDBNestedData() {
        if(compDBNestedData==null) {
            compDBNestedData = Database.getCompDBAssetToNestedDataMap();
        }
        return compDBNestedData;
    }
    protected String name;
    public ComputableCompDBAttribute(Collection<AbstractFilter.FilterType> filterTypes, String name) {
        super(filterTypes);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<T> attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        List<T> data = getCompDBNestedData().getOrDefault(item, Collections.emptyList()).stream().map(obj->{
            return (T)obj.get(name);
        }).filter(obj->obj!=null).distinct().collect(Collectors.toList());
        if(data.isEmpty()) return null;
        return data;
    }
}

