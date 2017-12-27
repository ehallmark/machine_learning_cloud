package user_interface.ui_models.charts;

import j2html.tags.ContainerTag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 12/16/2017.
 */
public abstract class AbstractPivotChart extends TableAttribute {

    public AbstractPivotChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes, Collection<AbstractAttribute> collectByAttrs, CollectorType defaultCollectType, String name) {
        super(attributes,groupedByAttributes,collectByAttrs,defaultCollectType,name);
    }


    @Override
    public List<TableResponse> createTables(PortfolioList portfolioList) {
        if(attrNames==null||attrNames.isEmpty()) return Collections.emptyList();
        System.out.println("Table attr list: "+String.join("; ",attrNames));
        return Stream.of(attrNames).flatMap(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute->SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + (humanAttrs.isEmpty() ? "" :  " by "+ (humanAttrs.isEmpty() ? "*BLANK*" : String.join(", ",humanAttrs)));
            List<String> groupedBy = attrNameToGroupByAttrNameMap.get("");
            Integer maxLimit = attrNameToMaxGroupSizeMap.get("");
            Boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault("",false);
            if(maxLimit==null) maxLimit = 1;
            return Stream.of(createHelper(portfolioList.getItemList(), attrList, groupedBy, title, null, maxLimit, includeBlank));
        }).collect(Collectors.toList());
    }

    protected TableResponse createHelper(List<Item> data, List<String> rowAttrs, List<String> columnAttrs, String yTitle, String subTitle, int maxLimit, boolean includeBlank) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle + (subTitle!=null&&subTitle.length()>0 ? (" (Grouped by "+subTitle+")") : "");
        response.numericAttrNames = Collections.singleton(collectorType.toString());


        List<Pair<Item,DeepList<Object>>> columnGroups = columnAttrs == null ? Collections.emptyList() : groupTableData(data,columnAttrs);
        List<Pair<Item,DeepList<Object>>> rowGroups = rowAttrs == null ? Collections.emptyList() : groupTableData(data,rowAttrs);

        System.out.println("Row groups size: "+rowGroups.size());
        System.out.println("Column groups size: "+columnGroups.size());

        System.out.println("Starting to group table...");
        Collector<Pair<Item,DeepList<Object>>,?,? extends Number> collector = getCollectorFromCollectorType();

        // build matrix
        List<Pair<DeepList<Object>,Set<Item>>> columnData = collectData(columnGroups, maxLimit);
        List<Pair<DeepList<Object>,Set<Item>>> rowData = collectData(rowGroups, -1);

        response.headers = new ArrayList<>();
        response.headers.addAll(rowAttrs);
        response.headers.add("Column-wise "+collectorType.toString());
        response.nonHumanAttrs = columnData.stream().map(d->{
            AtomicBoolean containsBlank = new AtomicBoolean(false);
            String label = String.join("; ",d.getFirst().stream().map(i->{
                String str = i.toString();
                if(str.isEmpty()){
                    containsBlank.set(true);
                    return "*BLANK*";
                }
                return str;
            }).collect(Collectors.toList()));
            if(containsBlank.get()&&!includeBlank) {
                return null;
            } else {
                return label;
            }
        }).filter(label->label!=null).collect(Collectors.toList());
        response.headers.addAll(response.nonHumanAttrs);
        System.out.println("Row attrs: "+rowAttrs);
        System.out.println("Column colAttrs: "+columnAttrs);
        System.out.println("Row data size: "+rowData.size());
        System.out.println("Column data size: "+columnData.size());

        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                return rowData.stream().map(rowPair->{
                    if(includeBlank || !rowPair.getFirst().stream().anyMatch(p->p==null||p.toString().length()==0)) {
                        Map<String, String> row = new HashMap<>();
                        Set<Item> rowSet = rowPair.getSecond();
                        for(int i = 0; i < rowAttrs.size(); i++) {
                            row.put(rowAttrs.get(i),rowPair.getFirst().get(i).toString());
                        }
                        List<Number> totals = new ArrayList<>();
                        columnData.forEach(colPair -> {
                            if(includeBlank || !colPair.getFirst().stream().anyMatch(p->p==null||p.toString().length()==0)) {
                                List<Item> intersection = rowSet.stream().filter(item -> colPair.getSecond().contains(item)).collect(Collectors.toList());
                                Number value = intersection.stream().map(item -> new Pair<>(item, new DeepList<>())).collect(collector);
                                if(value!=null) {
                                    totals.add(value);
                                }
                                String valueStr;
                                if(value != null && (value instanceof Double || value instanceof Float)) {
                                    valueStr = String.format("%.2f", value.doubleValue());
                                } else if(value != null) {
                                    valueStr = value.toString();
                                } else {
                                    valueStr = "";
                                }
                                row.put(String.join("; ", colPair.getFirst().stream().map(i -> {
                                    String str = i.toString();
                                    if(str.isEmpty()) return "*BLANK*";
                                    return str;
                                }).collect(Collectors.toList())), valueStr);
                            }
                        });
                        Number total = null;
                        switch(collectorType) {
                            case Sum: {
                                total = totals.stream().mapToDouble(d->d.doubleValue()).sum();
                                break;
                            }
                            case Average: {
                                total = totals.stream().mapToDouble(d->d.doubleValue()).average().orElse(Double.NaN);
                                break;
                            }
                            case Min: {
                                total = totals.stream().mapToDouble(d->d.doubleValue()).min().orElse(Double.NaN);
                                break;
                            }
                            case Max: {
                                total = totals.stream().mapToDouble(d->d.doubleValue()).max().orElse(Double.NaN);
                                break;
                            } case Count: {
                                total = totals.stream().mapToInt(d->d.intValue()).sum();
                                break;
                            }
                        }
                        if(total!=null) {
                            row.put(collectorType.toString(), total.toString());
                        }
                        return row;
                    } else {
                        return null;
                    }
                }).filter(row->row!=null).collect(Collectors.toList());

            }
        };
        response.computeAttributesTask.fork();
        return response;
    }



}
