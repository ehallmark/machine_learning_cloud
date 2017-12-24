package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import j2html.tags.Tag;
import lombok.Getter;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.charts.tables.DeepList;
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
    public static final String PLOT_GROUPS_ON_SAME_CHART_FIELD = "plotGroupsSameChart";
    public static final String MAX_GROUP_FIELD = "maxGroupSizeField";
    protected Collection<String> searchTypes;
    @Getter
    protected List<String> attrNames;
    protected Collection<AbstractAttribute> groupByAttributes;
    @Getter
    protected Map<String,List<String>> attrNameToGroupByAttrNameMap;
    @Getter
    protected Map<String,Integer> attrNameToMaxGroupSizeMap;
    @Getter
    protected Map<String,Boolean> attrToPlotOnSameChartMap;
    @Getter
    protected String name;
    protected boolean groupByPerAttribute;
    protected boolean groupsPlottableOnSameChart;
    public AbstractChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttributes, String name, boolean groupByPerAttribute, boolean groupsPlottableOnSameChart) {
        super(attributes);
        this.groupByAttributes=groupByAttributes;
        this.groupsPlottableOnSameChart=groupsPlottableOnSameChart;
        //if(groupByAttributes!=null)groupByAttributes.forEach(attr->attr.setParent(this));
        this.name=name;
        this.attrNameToGroupByAttrNameMap = Collections.synchronizedMap(new HashMap<>());
        this.attrNameToMaxGroupSizeMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToPlotOnSameChartMap= Collections.synchronizedMap(new HashMap<>());
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

    protected String idFromName(String attrName) {
        if(attrName==null) return "";
        return attrName.replace(getName().replace("[", "").replace("]", "") + ".", "").replace(".", "");
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function2<Tag,Tag,Tag> combineTagFunction = (tag1,tag2) -> div().with(tag1,tag2);
        return this.getOptionsTag(userRoleFunction,null,null,combineTagFunction,groupByPerAttribute);
    }

    private Tag getPlotGroupsTogetherTag(String attrName) {
        attrName = getGroupByChartFieldName(idFromName(attrName));
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Plot Groups Together").attr("title","Plot groups together on the same chart.").with(
                                input().attr("style","float: left;").withValue("off").withId(attrName+ PLOT_GROUPS_ON_SAME_CHART_FIELD).withName(attrName+PLOT_GROUPS_ON_SAME_CHART_FIELD).withType("checkbox")
                        )
                )
        );
    }

    @Override
    protected Tag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,Tag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<Tag,Tag,Tag> combineTagFunction, boolean perAttr) {
        Function<String,Tag> newTagFunction;
        Function<String,List<String>> newAdditionalIdsFunction;
        if(groupByAttributes!=null) {
            Function<String,Tag> groupByFunction = attrName -> getGroupedByFunction(attrName,userRoleFunction);
            if(groupsPlottableOnSameChart) {
                newTagFunction = attrName -> combineTagFunction.apply(
                        div().withClass("row").with(
                                div().withClass("col-10").with(groupByFunction.apply(attrName)),
                                div().withClass("col-2").with(getPlotGroupsTogetherTag(attrName))
                        ),
                        additionalTagFunction==null?div():additionalTagFunction.apply(attrName)
                );
            } else {
                newTagFunction = additionalTagFunction == null ? groupByFunction : attrName -> combineTagFunction.apply(groupByFunction.apply(attrName),additionalTagFunction.apply(attrName));
            }
            if(groupByPerAttribute) {
                Function<String, List<String>> groupedByInputIdsFunction = attrName -> {
                    String groupById = getGroupByChartFieldName(idFromName(attrName));
                    String groupByMaxLimit = groupById + MAX_GROUP_FIELD;
                    return Arrays.asList(groupById, groupByMaxLimit);
                };
                Function<String,List<String>> _newAdditionalIdsFunction = additionalInputIdsFunction == null ? groupedByInputIdsFunction : attrName -> {
                    return Stream.of(
                            groupedByInputIdsFunction.apply(attrName),
                            additionalInputIdsFunction.apply(attrName)
                    ).flatMap(list -> list.stream()).collect(Collectors.toList());
                };
                if(groupsPlottableOnSameChart) {
                    newAdditionalIdsFunction = attrName->{
                        return Stream.of(
                                _newAdditionalIdsFunction.apply(attrName),
                                Collections.singletonList(getGroupByChartFieldName(idFromName(attrName)) + PLOT_GROUPS_ON_SAME_CHART_FIELD)
                        ).flatMap(list -> list.stream()).collect(Collectors.toList());
                    };
                } else {
                    newAdditionalIdsFunction = _newAdditionalIdsFunction;
                }
            } else {
                newAdditionalIdsFunction = additionalInputIdsFunction;
            }

        } else {
            newTagFunction = additionalTagFunction;
            newAdditionalIdsFunction = additionalInputIdsFunction;
        }
        return super.getOptionsTag(userRoleFunction,newTagFunction,newAdditionalIdsFunction,(tag1,tag2)->div().with(tag1,tag2),groupByPerAttribute);
    }

    protected Tag getGroupedByFunction(String attrName,Function<String,Boolean> userRoleFunction) {
        String id = getGroupByChartFieldName(idFromName(attrName));
        List<AbstractAttribute> availableGroups = groupByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        String clazz = "form-control multiselect";
        return div().withClass("row").with(
                div().withClass("col-9").with(
                        label("Group By").attr("style","width: 100%;").with(
                                br(),
                                SimilarPatentServer.technologySelectWithCustomClass(id+"[]",id,clazz, groupedGroupAttrs,null)
                        )
                ),div().withClass("col-3").with(
                        label("Max Groups").attr("style","width: 100%;").with(
                                br(), input().withClass("form-control").withType("number").attr("style","height: 28px;").attr("min","0").withId(id+MAX_GROUP_FIELD).withName(id+MAX_GROUP_FIELD).withValue("10")
                        )
                )
        );
    }

    protected Stream<Pair<String,PortfolioList>> groupPortfolioListForGivenAttribute(PortfolioList portfolioList, String attribute) {
        List<String> groupedBy = attrNameToGroupByAttrNameMap.get(attribute);
        Integer maxLimit = attrNameToMaxGroupSizeMap.get(attribute);
        if(maxLimit==null) maxLimit = 1;
        return portfolioList.groupedBy(groupedBy).sorted((p1,p2)->Integer.compare(p2.getSecond().getItemList().size(),p1.getSecond().getItemList().size())).limit(maxLimit);
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
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);

        if(groupByAttributes!=null) {
            List<String> attrsToCheck = new ArrayList<>();
            if(groupByPerAttribute) {
                attrsToCheck.addAll(attrNames);
            } else {
                attrsToCheck.add("");
            }
            attrsToCheck.forEach(attrName->{
                String groupId = getGroupByChartFieldName(attrName);
                List<String> group = SimilarPatentServer.extractArray(params, groupId+"[]");
                if(group.size()>0) {
                    attrNameToGroupByAttrNameMap.put(attrName, group);
                }
                String groupSizeId = groupId + MAX_GROUP_FIELD;
                String groupSize = SimilarPatentServer.extractString(params, groupSizeId,"10");
                if(groupSize.length()>0) {
                    attrNameToMaxGroupSizeMap.put(attrName, Integer.valueOf(groupSize));
                }

                if(groupsPlottableOnSameChart) {
                    String plotOnSameChartId = groupId+PLOT_GROUPS_ON_SAME_CHART_FIELD;
                    boolean plotOnSameChart = SimilarPatentServer.extractBool(params, plotOnSameChartId);
                    attrToPlotOnSameChartMap.put(attrName,plotOnSameChart);
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
