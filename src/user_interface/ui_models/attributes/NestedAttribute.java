package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;


/**
 * Created by Evan on 5/9/2017.
 */
public abstract class NestedAttribute extends AbstractAttribute {
    @Getter
    protected Collection<AbstractAttribute> attributes;

    public NestedAttribute(Collection<AbstractAttribute> attributes) {
        super(Arrays.asList(AbstractFilter.FilterType.Nested));
        this.attributes = new ArrayList<>(attributes == null ? Collections.emptyList() : attributes);
        this.attributes.forEach(attr->{
           attr.setParent(this);
        });
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.technologySelectWithCustomClass(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,"multiselect nested-attribute-select "+getName(), attributes.stream().map(attr->getName()+"."+attr.getName()).collect(Collectors.toList()))
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

    @Override
    public Tag getDescription(AbstractFilter filter) {
        return div().with(
                attributes.stream().map(attr->attr.getDescription()).collect(Collectors.toList())
        );
    }

}
