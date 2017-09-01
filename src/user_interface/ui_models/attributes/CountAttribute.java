package user_interface.ui_models.attributes;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CountAttribute extends AbstractAttribute {
    @Getter
    private String name;
    public CountAttribute(String name) {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
        this.name=name;
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

    // FOR NOW
    @Override
    public boolean isNotYetImplemented() {
        return true;
    }

}
