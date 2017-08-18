package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;

/**
 * Created by ehallmark on 8/18/17.
 */
public abstract class AbstractScriptAttribute extends AbstractAttribute {
    public AbstractScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    @Override
    public String getType() {
        throw new RuntimeException("Abstract script attributes do not have types.");
    }

    public abstract Script getScript();

    @Override
    public boolean supportedByElasticSearch() {
        return false;
    }

}
