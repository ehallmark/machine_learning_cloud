package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.charts.aggregate_charts.AggregatePivotChart;
import user_interface.ui_models.charts.aggregate_charts.AggregationChart;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractChartAttribute extends NestedAttribute implements DependentAttribute<AbstractChartAttribute> {
    public static final String MAX_GROUP_FIELD = "maxGroupSizeField";
    public static final String INCLUDE_BLANK_FIELD = "includeBlanks";
    public static final int DEFAULT_MAX_SLICES = 25;
    protected static final Function2<ContainerTag,ContainerTag,ContainerTag> DEFAULT_COMBINE_BY_FUNCTION = (tag1,tag2) -> {
        return div().with(tag1,tag2);
    };
    @Getter
    protected Map<String,String> attrToCollectByAttrMap;
    @Getter
    protected Map<String,Type> attrToCollectTypeMap;
    @Getter
    protected Map<String,Boolean> attrToDrilldownMap;
    @Getter
    protected Map<String,Boolean> attrToSwapAxesMap;
    @Getter
    protected List<String> attrNames;
    @Getter
    protected Collection<AbstractAttribute> groupByAttributes;
    @Getter
    protected Map<String,String> attrNameToGroupByAttrNameMap;
    @Getter
    protected Map<String,Integer> attrNameToMaxGroupSizeMap;
    @Getter
    protected Map<String,Boolean> attrToPlotOnSameChartMap;
    @Getter
    protected String name;
    protected Map<String,Boolean> attrNameToIncludeBlanksMap;
    @Getter
    protected Collection<AbstractAttribute> collectByAttributes;
    protected Map<String,Integer> attrToLimitMap;
    protected Map<String,Boolean> attrToIncludeRemainingMap;
    public AbstractChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttributes, Collection<AbstractAttribute> collectByAttributes, String name) {
        super(attributes);
        this.groupByAttributes=groupByAttributes;
        this.collectByAttributes=collectByAttributes;
        //if(groupByAttributes!=null)groupByAttributes.forEach(attr->attr.setParent(this));
        this.name=name;
        this.attrNameToGroupByAttrNameMap = Collections.synchronizedMap(new HashMap<>());
        this.attrNameToMaxGroupSizeMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToPlotOnSameChartMap= Collections.synchronizedMap(new HashMap<>());
        this.attrNameToIncludeBlanksMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectByAttrMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectTypeMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToDrilldownMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToSwapAxesMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToLimitMap=Collections.synchronizedMap(new HashMap<>());
        this.attrToIncludeRemainingMap = Collections.synchronizedMap(new HashMap<>());
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        if(types.size()==1) {
            return SimilarPatentServer.humanAttributeFor(types.stream().findAny().get());
        } else {
            return "Assets";
        }
    }

    private String idFromName(String attrName) {
        if(attrName==null) return "";
        return attrName.replace(getName().replace("[", "").replace("]", "") + ".", "").replace(".", "");
    }

    @Override
    public ContainerTag getNestedOptions(Function<String,Boolean> userRoleFunction, Function<String,ContainerTag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<ContainerTag,ContainerTag,ContainerTag> combineTagFunction, boolean perAttr, boolean loadChildren, Map<String,String> idToTagMap) {
        Function<String,ContainerTag> newTagFunction;
        Function<String,List<String>> newAdditionalIdsFunction;
        if(groupByAttributes!=null) {
            Function<String,ContainerTag> groupByFunction = attrName -> getGroupedByFunction(attrName,userRoleFunction);

            newTagFunction = additionalTagFunction == null ? groupByFunction : attrName -> combineTagFunction.apply(groupByFunction.apply(attrName),additionalTagFunction.apply(attrName));

            Function<String, List<String>> groupedByInputIdsFunction = attrName -> {
                String groupById = getGroupByChartFieldName(attrName);
                String groupByMaxLimit = groupById + MAX_GROUP_FIELD;
                String groupByIncludeBlanks = groupById + INCLUDE_BLANK_FIELD;
                return Arrays.asList(groupById, groupByMaxLimit, groupByIncludeBlanks);
            };
            Function<String,List<String>> _newAdditionalIdsFunction = additionalInputIdsFunction == null ? groupedByInputIdsFunction : attrName -> {
                return Stream.of(
                        groupedByInputIdsFunction.apply(attrName),
                        additionalInputIdsFunction.apply(attrName)
                ).flatMap(list -> list.stream()).collect(Collectors.toList());
            };

            newAdditionalIdsFunction = _newAdditionalIdsFunction;

        } else {
            newTagFunction = additionalTagFunction;
            if (additionalInputIdsFunction == null) {
                newAdditionalIdsFunction = str -> Collections.emptyList();
            } else {
                newAdditionalIdsFunction = additionalInputIdsFunction;
            }
        }
        Function<String, ContainerTag> tagFunction;
        Function<String, List<String>> collectorIdsFunction;
        if(collectByAttributes!=null) {
            List<AbstractAttribute> availableGroups = collectByAttributes.stream().filter(attr -> attr.isDisplayable() && userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
            Map<String, List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter -> filter.getRootName())).entrySet()
                    .stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().map(attr -> attr.getFullName()).collect(Collectors.toList()))));
            Function<String, ContainerTag> collectorTagFunction = getCombineByTagFunction(groupedGroupAttrs);
            collectorIdsFunction = attrName -> {
                List<String> ids = Arrays.asList(getCollectByAttrFieldName(attrName), getCollectTypeFieldName(attrName));
                return ids;
            };
            tagFunction = str -> div().withClass("row").with(
                    div().withClass("col-12").with(
                            newTagFunction.apply(str)
                    ), div().withClass("col-12").with(
                            collectorTagFunction.apply(str)
                    )
            );
        } else {
            collectorIdsFunction = str -> Collections.emptyList();
            if (newTagFunction == null) {
                tagFunction = str -> div();
            } else {
                tagFunction = str -> div().withClass("row").with(
                        div().withClass("col-12").with(
                                newTagFunction.apply(str)
                        )
                );
            }
        }
        Function<String,List<String>> idsFunction = str -> {
            return Stream.of(collectorIdsFunction.apply(str), newAdditionalIdsFunction.apply(str)).flatMap(list->list.stream()).collect(Collectors.toList());
        };
        return super.getNestedOptions(userRoleFunction,tagFunction,idsFunction,DEFAULT_COMBINE_BY_FUNCTION, true, loadChildren,idToTagMap);
    }

    protected ContainerTag getGroupedByFunction(String attrName,Function<String,Boolean> userRoleFunction) {
        String id = getGroupByChartFieldName(attrName);
        List<AbstractAttribute> availableGroups = groupByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<Pair<String,String>>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->new Pair<>(attr.getFullName(), "")).collect(Collectors.toList()))));
        String clazz = "single-select2";
        return div().withClass("row").with(
                div().withClass("col-8").with(
                        label("Group By").attr("title","Creates a separate chart for each of the largest groups.").attr("style","width: 100%;").with(
                                br(),
                                SimilarPatentServer.technologySelectWithCustomClass(id,id,clazz, groupedGroupAttrs,"")
                        )
                ),div().withClass("col-2").with(
                        label("Max Groups").attr("title", "The maximum number of groups to build charts for (defaults to 10).").attr("style","width: 100%;").with(
                                br(), input().withClass("form-control").withType("number").attr("min","0").withId(id+MAX_GROUP_FIELD).withName(id+MAX_GROUP_FIELD).withValue("10")
                        )
                ),div().withClass("col-2").with(
                        label("Include Blanks").attr("title","Whether or not blank groups or values should be included.").attr("style","width: 100%;").with(
                                br(), input().withType("checkbox").withId(id+INCLUDE_BLANK_FIELD).withName(id+INCLUDE_BLANK_FIELD).withValue("10")
                        )
                )
        );
    }

    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row collect-container").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            select().attr("style","width:100%;").withName(getCollectByAttrFieldName(attrName)).withId(getCollectByAttrFieldName(attrName)).withClass("collect-by-select")
                                    .with(option("").withValue(""))
                                    .with(
                                            groupedGroupAttrs.entrySet().stream().map(e-> {
                                                String optGroup = e.getKey();
                                                return optgroup().attr("label",SimilarPatentServer.humanAttributeFor(optGroup)).attr("name",optGroup).with(
                                                        e.getValue().stream().map(technology->{
                                                            return div().with(option(SimilarPatentServer.humanAttributeFor(technology)).withValue(technology));
                                                        }).collect(Collectors.toList())
                                                );
                                            }).collect(Collectors.toList())
                                    )
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2 collect-type").withName(getCollectTypeFieldName(attrName)).withId(getCollectTypeFieldName(attrName))
                    )
            );
        };
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

        if(groupByAttributes!=null) {
            List<String> attrsToCheck = new ArrayList<>();
            attrsToCheck.addAll(attrNames);
            attrsToCheck.forEach(attrName->{
                String groupId = getGroupByChartFieldName(attrName);
                String group = SimilarPatentServer.extractString(params, groupId, null);
                if(group!=null) {
                    attrNameToGroupByAttrNameMap.put(attrName, group);
                }
                String groupSizeId = groupId + MAX_GROUP_FIELD;
                Integer groupSize = SimilarPatentServer.extractInt(params, groupSizeId,10);
                if(groupSize<1) {
                    groupSize = 1;
                }
                attrNameToMaxGroupSizeMap.put(attrName, groupSize);

                String groupIncludeBlanksId = groupId + INCLUDE_BLANK_FIELD;
                boolean groupIncludeBlanks = SimilarPatentServer.extractBool(params, groupIncludeBlanksId);
                attrNameToIncludeBlanksMap.put(attrName, groupIncludeBlanks);

            });
        }

        if(attrNames!=null) {
            this.attrNames.forEach(attr -> {
                boolean drilldown = SimilarPatentServer.extractBool(params, getDrilldownAttrFieldName(attr));
                if(drilldown && !attrNameToGroupByAttrNameMap.containsKey(attr)) {
                    throw new RuntimeException("While building charts... Must specify group by attribute to use 'Drilldown' for "+SimilarPatentServer.humanAttributeFor(attr)+". Please unselect 'Drilldown' in chart options or select a group by attribute.");
                }
                attrToDrilldownMap.put(attr,drilldown);
                boolean swapAxes = SimilarPatentServer.extractBool(params, getSwapAxesAttrFieldName(attr));
                if(swapAxes && !drilldown) {
                    throw new RuntimeException("While building charts... Must use 'Drilldown' feature to use 'Swap Axes' for "+SimilarPatentServer.humanAttributeFor(attr)+". Please unselect 'Swap Axes' in chart options or select 'Drilldown'.");
                }
                attrToSwapAxesMap.put(attr,swapAxes);
                System.out.println("Looking for field: "+attr+" -> "+getCollectByAttrFieldName(attr));
                String collectByName = SimilarPatentServer.extractString(params, getCollectByAttrFieldName(attr), null);
                if(collectByName!=null) attrToCollectByAttrMap.put(attr,collectByName);
                String collectByType = SimilarPatentServer.extractString(params, getCollectTypeFieldName(attr), Type.Count.toString());
                if(collectByType==null) collectByType = Type.Count.toString();
                System.out.println("Found type: "+collectByType);
                attrToCollectTypeMap.put(attr,Type.valueOf(collectByType));
                Integer limit = SimilarPatentServer.extractInt(params, getMaxSlicesField(attr), this instanceof AggregatePivotChart ? AggregationChart.MAXIMUM_AGGREGATION_SIZE : DEFAULT_MAX_SLICES);
                if(limit!=null) attrToLimitMap.put(attr,limit);
                boolean includeRemaining = SimilarPatentServer.extractBool(params, getIncludeRemainingField(attr));
                attrToIncludeRemainingMap.put(attr, includeRemaining);
            });
        }
    }

    protected String getCollectByAttrFieldName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.COLLECT_BY_ATTR_FIELD);
    }

    protected String getCollectTypeFieldName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.COLLECT_TYPE_FIELD);
    }

    protected String getSwapAxesAttrFieldName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.SWAP_AXES_FIELD);
    }

    protected String getDrilldownAttrFieldName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.DRILLDOWN_BOOL_FIELD);
    }

    protected String getMaxSlicesField(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.MAX_SLICES_FIELD);
    }

    protected String getIncludeRemainingField(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.INCLUDE_REMAINING_FIELD);
    }


    protected String getStatsAggName(String attrName) {
        return handleAttrName(attrName, "_mStat");
    }

    protected String getGroupByChartFieldName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.CHARTS_GROUPED_BY_FIELD);
    }

    protected String getChartMinByName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.LINE_CHART_MIN);
    }


    protected String getChartMaxByName(String attrName) {
        return handleAttrName(attrName, SimilarPatentServer.LINE_CHART_MAX);
    }


    private String handleAttrName(String attrName, String field) {
        attrName = idFromName(attrName);
        return (getName().replace("[","").replace("]","")+ field +(attrName==null?"":attrName)).replace(".","").replace("[]","");
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
