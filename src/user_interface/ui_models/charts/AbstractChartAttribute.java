package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.google.elasticsearch.attributes.SimilarityAttribute;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.charts.aggregations.Type;
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
    public static final String INCLUDE_BLANK_FIELD = "includeBlanks";
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
    protected Collection<AbstractAttribute> groupByAttributes;
    @Getter
    protected Map<String,String> attrNameToGroupByAttrNameMap;
    @Getter
    protected Map<String,Integer> attrNameToMaxGroupSizeMap;
    @Getter
    protected Map<String,Boolean> attrToPlotOnSameChartMap;
    @Getter
    protected String name;
    protected boolean groupByPerAttribute;
    protected boolean groupsPlottableOnSameChart;
    protected Map<String,Boolean> attrNameToIncludeBlanksMap;
    protected Collection<AbstractAttribute> collectByAttributes;
    @Setter
    protected Map<String,SimilarityAttribute> similarityModels;
    public AbstractChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttributes, Collection<AbstractAttribute> collectByAttributes, String name, boolean groupByPerAttribute, boolean groupsPlottableOnSameChart) {
        super(attributes);
        this.groupByAttributes=groupByAttributes;
        this.collectByAttributes=collectByAttributes;
        this.groupsPlottableOnSameChart=groupsPlottableOnSameChart;
        //if(groupByAttributes!=null)groupByAttributes.forEach(attr->attr.setParent(this));
        this.name=name;
        this.attrNameToGroupByAttrNameMap = Collections.synchronizedMap(new HashMap<>());
        this.attrNameToMaxGroupSizeMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToPlotOnSameChartMap= Collections.synchronizedMap(new HashMap<>());
        this.attrNameToIncludeBlanksMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectByAttrMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectTypeMap = Collections.synchronizedMap(new HashMap<>());
        this.groupByPerAttribute = groupByPerAttribute;
        this.attrToDrilldownMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToSwapAxesMap = Collections.synchronizedMap(new HashMap<>());
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
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return this.getOptionsTag(userRoleFunction,null,null,DEFAULT_COMBINE_BY_FUNCTION,groupByPerAttribute);
    }

    private ContainerTag getPlotGroupsTogetherTag(String attrName) {
        attrName = getGroupByChartFieldName(attrName);
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Plot Groups Together").attr("title","Plot groups together on the same chart.").with(
                                input().attr("style","float: left;").withValue("off").withId(attrName+ PLOT_GROUPS_ON_SAME_CHART_FIELD).withName(attrName+PLOT_GROUPS_ON_SAME_CHART_FIELD).withType("checkbox")
                        )
                )
        );
    }

    private ContainerTag getDrillDownTag(String attrName) {
        attrName = getDrilldownAttrFieldName(attrName);
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Drilldown").attr("title","Plot groups using drilldowns.").with(
                                input().attr("style","float: left;").withValue("off").withId(attrName).withName(attrName).withType("checkbox")
                        )
                )
        );
    }

    @Override
    protected ContainerTag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,ContainerTag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<ContainerTag,ContainerTag,ContainerTag> combineTagFunction, boolean perAttr) {
        Function<String,ContainerTag> newTagFunction;
        Function<String,List<String>> newAdditionalIdsFunction;
        if(groupByAttributes!=null) {
            Function<String,ContainerTag> groupByFunction = attrName -> getGroupedByFunction(attrName,userRoleFunction);
            if(groupsPlottableOnSameChart) {
                newTagFunction = attrName -> combineTagFunction.apply(
                        div().withClass("row").with(
                                div().withClass("col-10").with(
                                        groupByFunction.apply(attrName)
                                ),div().withClass("col-2").with(
                                        getPlotGroupsTogetherTag(attrName)
                                )
                        ),additionalTagFunction==null?div():additionalTagFunction.apply(attrName)
                );
            } else {
                newTagFunction = additionalTagFunction == null ? groupByFunction : attrName -> combineTagFunction.apply(groupByFunction.apply(attrName),additionalTagFunction.apply(attrName));
            }
            if(groupByPerAttribute) {
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
                if(groupsPlottableOnSameChart) {
                    newAdditionalIdsFunction = attrName->{
                        return Stream.of(
                                _newAdditionalIdsFunction.apply(attrName),
                                Collections.singletonList(getGroupByChartFieldName(attrName) + PLOT_GROUPS_ON_SAME_CHART_FIELD)
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
        List<AbstractAttribute> availableGroups = collectByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        Function<String,ContainerTag> collectorTagFunction = getCombineByTagFunction(groupedGroupAttrs);
        Function<String,List<String>> collectorIdsFunction = attrName -> {
            List<String> ids = Arrays.asList(getCollectByAttrFieldName(attrName),getCollectTypeFieldName(attrName));
            return ids;
        };
        Function<String, ContainerTag> tagFunction = str -> div().withClass("row").with(
                div().withClass("col-12").with(
                        newTagFunction.apply(str)
                ), div().withClass("col-12").with(
                        collectorTagFunction.apply(str)
                )
        );
        Function<String,List<String>> idsFunction = str -> {
            return Stream.of(collectorIdsFunction.apply(str), newAdditionalIdsFunction.apply(str)).flatMap(list->list.stream()).collect(Collectors.toList());
        };
        return super.getOptionsTag(userRoleFunction,tagFunction,idsFunction,DEFAULT_COMBINE_BY_FUNCTION,groupByPerAttribute);
    }

    protected ContainerTag getGroupedByFunction(String attrName,Function<String,Boolean> userRoleFunction) {
        String id = getGroupByChartFieldName(attrName);
        List<AbstractAttribute> availableGroups = groupByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        String clazz = "single-select2";
        return div().withClass("row").with(
                div().withClass("col-8").with(
                        label("Group By").attr("title","Creates a separate chart for each of the largest groups.").attr("style","width: 100%;").with(
                                br(),
                                SimilarPatentServer.technologySelectWithCustomClass(id,id,clazz, groupedGroupAttrs,"")
                        )
                ),div().withClass("col-2").with(
                        label("Max Groups").attr("title", "The maximum number of groups to build charts for.").attr("style","width: 100%;").with(
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


    protected Stream<Pair<String,PortfolioList>> groupPortfolioListForGivenAttribute(PortfolioList portfolioList, String attribute) {
        String groupedBy = attrNameToGroupByAttrNameMap.get(attribute);
        Integer maxLimit = attrNameToMaxGroupSizeMap.get(attribute);
        Boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attribute,false);
        if(maxLimit==null) maxLimit = 1;
        List<String> groupByList = new ArrayList<>();
        if(groupedBy!=null) {
            groupByList.add(groupedBy);
        }
        return portfolioList.groupedBy(groupByList).filter(g->includeBlank||!g.getSecond().isBlankGroup()).sorted((p1,p2)->Integer.compare(p2.getSecond().getItemList().size(),p1.getSecond().getItemList().size())).limit(maxLimit);
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
            if(groupByPerAttribute) {
                attrsToCheck.addAll(attrNames);
            } else {
                attrsToCheck.add("");
            }
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

                if(groupsPlottableOnSameChart) {
                    String plotOnSameChartId = groupId+PLOT_GROUPS_ON_SAME_CHART_FIELD;
                    boolean plotOnSameChart = SimilarPatentServer.extractBool(params, plotOnSameChartId);
                    attrToPlotOnSameChartMap.put(attrName,plotOnSameChart);
                }

            });
        }

        if(attrNames!=null) {
            this.attrNames.forEach(attr -> {
                boolean drilldown = SimilarPatentServer.extractBool(params, getDrilldownAttrFieldName(attr));
                if(drilldown && !attrNameToGroupByAttrNameMap.containsKey(attr)) {
                    throw new RuntimeException("Must specify group by attribute to use 'Drilldown' for "+SimilarPatentServer.humanAttributeFor(attr)+".");
                }
                attrToDrilldownMap.put(attr,drilldown);
                boolean swapAxes = SimilarPatentServer.extractBool(params, getDrilldownAttrFieldName(attr));
                if(swapAxes && !drilldown) {
                    throw new RuntimeException("Must use 'Drilldown' feature to use 'Swap Axes' for "+SimilarPatentServer.humanAttributeFor(attr)+".");
                }
                attrToSwapAxesMap.put(attr,swapAxes);
                System.out.println("Looking for field: "+attr+" -> "+getCollectByAttrFieldName(attr));
                String collectByName = SimilarPatentServer.extractString(params, getCollectByAttrFieldName(attr), null);
                if(collectByName!=null) attrToCollectByAttrMap.put(attr,collectByName);
                String collectByType = SimilarPatentServer.extractString(params, getCollectTypeFieldName(attr), Type.Count.toString());
                if(collectByType==null) collectByType = Type.Count.toString();
                System.out.println("Found type: "+collectByType);
                attrToCollectTypeMap.put(attr,Type.valueOf(collectByType));
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
