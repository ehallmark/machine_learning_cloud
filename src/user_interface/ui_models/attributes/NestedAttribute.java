package user_interface.ui_models.attributes;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;


/**
 * Created by Evan on 5/9/2017.
 */
public abstract class NestedAttribute extends AbstractAttribute {
    @Getter
    protected Collection<AbstractAttribute> attributes;
    protected boolean setParent;
    protected boolean isObject;
    public NestedAttribute(Collection<AbstractAttribute> attributes, boolean setParent) {
        super(Arrays.asList(AbstractFilter.FilterType.Nested, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
        this.attributes = new ArrayList<>(attributes == null ? Collections.emptyList() : attributes);
        this.setParent=setParent;
        this.isObject=false;
        if(setParent){
            this.attributes.forEach(attr->{
                attr.setParent(this);
            });
        }
    }

    public NestedAttribute(Collection<AbstractAttribute> attributes) {
        this(attributes,true);
    }



    @Override
    public boolean isObject() {
        return isObject;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return getOptionsTag(userRoleFunction, null, false);
    }


    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,Tag> additionalTagFunction, boolean perAttr) {
        String styleString = "margin-left: 5%; margin-right: 5%; display: none;";
        String name = getFullName().replace(".","");
        String clazz = "nested-filter-select";
        String id = ("multiselect-"+clazz+"-"+name).replaceAll("[\\[\\] ]","");
        Tag groupbyTag = additionalTagFunction!=null&&!perAttr ? additionalTagFunction.apply(null) : null;
        List<AbstractAttribute> applicableAttributes = attributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        return div().with(
                div().with(
                        groupbyTag==null?span():groupbyTag,
                        SimilarPatentServer.technologySelectWithCustomClass(name+(name.endsWith("[]")?"":"[]"),id,clazz, applicableAttributes.stream().map(attr->attr.getFullName()).collect(Collectors.toList()))
                ), div().withClass("nested-form-list").with(
                        applicableAttributes.stream().map(filter->{
                            String collapseId = "collapse-filters-"+filter.getFullName().replaceAll("[\\[\\]]","");
                            Tag additionalTag = additionalTagFunction!=null&&perAttr ? additionalTagFunction.apply(filter.getFullName()) : null;
                            ContainerTag attrTag = div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(filter.getFullName(),null,collapseId,filter.getOptionsTag(userRoleFunction), id, filter.isNotYetImplemented(), filter.getDescription().render())
                            );
                            if(additionalTag!=null) attrTag = attrTag.with(additionalTag);
                            return attrTag;
                        }).collect(Collectors.toList())
                )
        );
    }

    @Override
    public String getType() {
        return isObject ? "object" : "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }

    @Override
    public Tag getDescription(AbstractFilter filter) {
        List<Tag> tagList = attributes.stream().map(attr->attr.getDescription()).collect(Collectors.toList());
        if(tagList.size()>10) {
            tagList = Stream.of(tagList,Arrays.asList(div().withText("And more..."))).flatMap(list->list.stream().limit(10)).collect(Collectors.toList());
        }
        return filter != null ? span().with(tagList) : div().withText("Contains the following nested attributes:").with(span().with(tagList));
    }

}
