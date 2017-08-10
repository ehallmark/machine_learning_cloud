package user_interface.ui_models.attributes.meta_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by ehallmark on 8/10/17.
 */
public abstract class MetaComputableAttribute<T> extends ComputableAttribute<T> {
    public MetaComputableAttribute() {
        super(Collections.emptyList());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        throw new UnsupportedOperationException("Meta attributes");
    }

    @Override
    public Collection<MetaComputableAttribute> getNecessaryMetaAttributes() {
        throw new UnsupportedOperationException("Meta attributes");
    }

}
