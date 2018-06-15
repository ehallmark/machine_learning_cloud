package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;

public class PCPublicationNumberFull extends AbstractAttribute {
    public PCPublicationNumberFull() {
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return Attributes.PC_PUBLICATION_NUMBER_FULL;
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
