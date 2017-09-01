package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 6/15/17.
 */
public class GatherTechnologyAttribute extends ComputableAttribute<String[]> {

    public GatherTechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.AdvancedKeyword));
    }

    @Override
    public String[] attributesFor(Collection<String> items, int limit) {
        Collection<String> tech = Database.getGatherPatentToTechnologyMap().get(items.stream().findAny().get());
        if(tech==null) return null;
        return tech.toArray(new String[tech.size()]);
    }

    @Override
    public String getName() {
        return Constants.GATHER_VALUE;
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

}

