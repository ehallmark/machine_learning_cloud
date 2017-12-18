package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractChartAttribute extends NestedAttribute implements DependentAttribute<AbstractChartAttribute> {
    protected Collection<String> searchTypes;
    @Getter
    protected List<String> attrNames;
    protected Collection<AbstractAttribute> groupByAttributes;
    @Getter
    protected Map<String,String> attrNameToGroupByAttrNameMap;
    @Getter
    protected String name;
    protected boolean groupByPerAttribute;
    public AbstractChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttributes, String name, boolean groupByPerAttribute) {
        super(attributes);
        this.groupByAttributes=groupByAttributes;
        //if(groupByAttributes!=null)groupByAttributes.forEach(attr->attr.setParent(this));
        this.name=name;
        this.attrNameToGroupByAttrNameMap = Collections.synchronizedMap(new HashMap<>());
        this.groupByPerAttribute = groupByPerAttribute;
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        if(types.size()==1) {
            return SimilarPatentServer.humanAttributeFor(types.stream().findAny().get());
        } else {
            return "Assets";
        }
    }

    protected String getGroupByChartFieldName(String attrName) {
        return (getName().replace("[","").replace("]","")+SimilarPatentServer.CHARTS_GROUPED_BY_FIELD+(attrName==null?"":attrName)).replace(".","");
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String, Tag> groupByFunction = null;
        if(groupByAttributes!=null) {
            groupByFunction = attrName -> getGroupedByFunction(attrName,userRoleFunction);
        }

        return super.getOptionsTag(userRoleFunction,groupByFunction,groupByPerAttribute);
    }

    protected Tag getGroupedByFunction(String attrName,Function<String,Boolean> userRoleFunction) {
        if(attrName!=null) {
            attrName = attrName.replace(getName().replace("[", "").replace("]", "") + ".", "").replace(".", "");
        }
        String id = getGroupByChartFieldName(attrName);
        List<AbstractAttribute> availableGroups = groupByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        String clazz = "form-control single-select2";
        return div().with(
                label("Group By"), br(),
                SimilarPatentServer.technologySelectWithCustomClass(id,id,clazz, groupedGroupAttrs,false)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        System.out.println("Starting params for: "+getName());
        attrNames = SimilarPatentServer.extractArray(params, getName());
        attrNames = attrNames.stream().flatMap(name->{
            List<String> children = SimilarPatentServer.extractArray(params, name.replace(".","")+"[]");
            if(children.size()>0) {
                return children.stream().map(child->child.substring(child.indexOf(".")+1));
            } else {
                return Stream.of(name.substring(name.indexOf(".")+1));
            }
        }).collect(Collectors.toList());
        System.out.println("Search attrs for "+getName()+": "+attrNames);
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);

        if(groupByAttributes!=null) {
            attrNames.forEach(attrName->{
                String group = SimilarPatentServer.extractString(params, getGroupByChartFieldName(getName()+attrName),"");
                if(group.length()>0) {
                    attrNameToGroupByAttrNameMap.put(attrName, group);
                }
            });
        }

        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
    }


    @Override
    public AbstractFilter.FieldType getFieldType() { throw new UnsupportedOperationException("fieldType not defined for charts.");}

    public Tag getDescription() {
        String text = Constants.ATTRIBUTE_DESCRIPTION_MAP.get(getType());
        if(text==null) return span();
        return div().with(
                div().withText(text)
        );
    }


}
