package user_interface.ui_models.attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by ehallmark on 6/15/17.
 */
public class SellerAttribute extends AbstractAttribute {
    public SellerAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
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
        return Constants.SELLER;
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
