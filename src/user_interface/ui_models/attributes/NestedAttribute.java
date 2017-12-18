package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.AbstractChartAttribute;
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
    public static final String CLAZZ = "nested-filter-select";
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

    public String getAttributeId() {
        return null;
    }

    public List<String> getInputIds() {
        return Collections.singletonList(getId());
    }

    public String getId() {
        String name = getFullName().replace(".","");
        String clazz = CLAZZ;
        return ("multiselect-"+clazz+"-"+name).replaceAll("[\\[\\] ]","");
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
        String clazz = CLAZZ;
        String id = getId();
        Tag groupbyTag = additionalTagFunction!=null&&!perAttr ? additionalTagFunction.apply(null) : null;
        List<AbstractAttribute> applicableAttributes = attributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        return div().with(
                div().with(
                        groupbyTag==null?span():groupbyTag,
                        SimilarPatentServer.technologySelectWithCustomClass(name+(name.endsWith("[]")?"":"[]"),id,clazz, applicableAttributes.stream().map(attr->attr.getFullName()).collect(Collectors.toList()))
                ), div().withClass("nested-form-list").with(
                        applicableAttributes.stream().map(filter->{
                            String collapseId = "collapse-filters-"+filter.getFullName().replaceAll("[\\[\\]]","");
                            Tag childTag;
                            if(filter instanceof NestedAttribute && ! (filter instanceof AbstractChartAttribute)) {
                                childTag = ((NestedAttribute) filter).getOptionsTag(userRoleFunction,additionalTagFunction,perAttr);
                            } else {
                                childTag = filter.getOptionsTag(userRoleFunction);
                                Tag additionalTag = additionalTagFunction!=null&&perAttr ? additionalTagFunction.apply(filter.getFullName()) : null;
                                if(additionalTag!=null) childTag = div().with(additionalTag,childTag);
                            }
                            return div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(filter.getFullName(),null,collapseId,childTag, id, filter.getAttributeId(), filter.getInputIds(), filter.isNotYetImplemented(), filter.getDescription().render())
                            );
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
