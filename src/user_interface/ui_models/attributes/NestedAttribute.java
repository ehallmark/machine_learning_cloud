package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import user_interface.ui_models.filters.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.label;


/**
 * Created by Evan on 5/9/2017.
 */
public abstract class NestedAttribute<T> extends AbstractAttribute<T> {
    @Getter
    protected Collection<AbstractAttribute> attributes;

    public NestedAttribute(Collection<AbstractAttribute> attributes) {
        super(Arrays.asList(AbstractFilter.FilterType.Nested));
        this.attributes = attributes;
    }

    @Override
    public Tag getOptionsTag() {
        return div().withClass("row").with(
                attributes.stream().map(attr->label(attr.getName())).collect(Collectors.toList())
        );
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }

}
