package user_interface.ui_models.attributes;

import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import seeding.google.elasticsearch.attributes.NonStoredTextAttribute;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregate_charts.AggregatePivotChart;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;


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

    public NestedAttribute clone() {
        return new NestedAttribute(attributes.stream().map(attr->attr.clone()).collect(Collectors.toList()),setParent) {
            @Override
            public boolean isObject() {
                return isObject;
            }
            @Override
            public String getName() {
                return null;
            }
        };
    }

    public String getAttributeId() {
        return null;
    }

    @Override
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
        return getOptionsTag(userRoleFunction, null, null, (tag1,tag2)->div().with(tag1,tag2), false);
    }


    protected ContainerTag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,ContainerTag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<ContainerTag,ContainerTag,ContainerTag> combineTagFunction, boolean perAttr) {
        String styleString = "margin-left: 5%; margin-right: 5%; display: none;";
        String name = getFullName().replace(".","");
        String clazz = CLAZZ;
        String id = getId();
        Tag groupbyTag = additionalTagFunction!=null&&!perAttr ? additionalTagFunction.apply(null) : null;
        List<AbstractAttribute> applicableAttributes = attributes.stream().filter(attr->attr.isDisplayable()&&!(attr instanceof NonStoredTextAttribute)&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        return div().with(
                div().with(
                        groupbyTag==null?span():div().with(
                                groupbyTag,br(),
                                p(this instanceof AggregatePivotChart ? "Row Attributes" : "Column Attributes")
                        ),
                        SimilarPatentServer.technologySelectWithCustomClass(name+(name.endsWith("[]")?"":"[]"),id,clazz, applicableAttributes.stream().map(attr->attr.getFullName()).collect(Collectors.toList()))
                ), div().withClass("nested-form-list").with(
                        applicableAttributes.stream().map(filter->{
                            String collapseId = "collapse-filters-"+filter.getFullName().replaceAll("[\\[\\].]","");
                            Tag childTag;
                            List<String> inputIds = new ArrayList<>();
                            if(filter.getInputIds()!=null) {
                                inputIds.addAll(filter.getInputIds());
                            }
                            if(filter instanceof NestedAttribute && ! (filter instanceof AbstractChartAttribute) && perAttr) {
                                childTag = ((NestedAttribute) filter).getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineTagFunction, perAttr);
                            } else {
                                childTag = filter.getOptionsTag(userRoleFunction);
                                ContainerTag additionalTag = additionalTagFunction!=null&&perAttr ? additionalTagFunction.apply(filter.getFullName()) : null;
                                if(additionalTag!=null) {
                                    childTag = combineTagFunction.apply(additionalTag,(ContainerTag)childTag);
                                }
                                if(additionalInputIdsFunction!=null) {
                                    List<String> additional = additionalInputIdsFunction.apply(filter.getFullName());
                                    if(additional!=null) {
                                        inputIds.addAll(additional);
                                    }
                                }
                            }
                            String attrName = filter.getFullName();
                            String humanName = SimilarPatentServer.humanAttributeFor(attrName);
                            if(inputIds.isEmpty()) inputIds = null;
                            return div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(humanName, attrName,null,collapseId,childTag, id, filter.getAttributeId(), inputIds, filter.isNotYetImplemented(), filter.getDescription().render())
                            );
                        }).collect(Collectors.toList())
                )
        );
    }

    @Override
    public String getType() {
        return isObject() ? "object" : "nested";
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
