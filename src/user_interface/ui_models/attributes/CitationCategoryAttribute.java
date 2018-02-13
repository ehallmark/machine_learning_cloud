package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CitationCategoryAttribute extends AbstractAttribute {

    public CitationCategoryAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp,AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.CITATION_CATEGORY;
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
        return Arrays.asList("cited by examiner", "cited by other");
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
