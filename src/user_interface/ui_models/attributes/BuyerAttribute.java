package user_interface.ui_models.attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by ehallmark on 6/15/17.
 */
public class BuyerAttribute extends ComputableAttribute<List<String>> {
    public BuyerAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword));
    }
    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public String getName() {
        return Constants.BUYER;
    }

    @Override
    public Map<String,Object> getNestedFields() {
        Map<String,Object> fields = new HashMap<>();
        Map<String,String> rawType = new HashMap<>();
        rawType.put("type","keyword");
        fields.put("raw",rawType);
        return fields;
    }

    @Override
    public List<String> attributesFor(Collection<String> portfolio, int limit) {
        if(patentDataMap==null) initMaps();
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item==null) return null;
        return patentDataMap.get(item);
    }
}
