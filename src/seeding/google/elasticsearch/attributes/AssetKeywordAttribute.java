package seeding.google.elasticsearch.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public abstract class AssetKeywordAttribute extends AbstractAttribute {
    public AssetKeywordAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.IncludeWithRelated, AbstractFilter.FilterType.ExcludeWithRelated, AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }
}
