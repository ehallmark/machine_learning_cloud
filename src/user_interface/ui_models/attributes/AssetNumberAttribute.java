package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class AssetNumberAttribute extends AbstractAttribute {
    public AssetNumberAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.IncludeWithRelated, AbstractFilter.FilterType.ExcludeWithRelated));
    }
    @Override
    public String getName() {
        return Constants.NAME;
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
