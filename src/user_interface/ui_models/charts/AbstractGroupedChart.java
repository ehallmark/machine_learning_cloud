package user_interface.ui_models.charts;

import org.nd4j.linalg.primitives.Pair;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractGroupedChart extends TableAttribute {


    public AbstractGroupedChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes, Collection<AbstractAttribute> collectByAttrs, CollectorType defaultCollectType, String name) {
        super(attributes,groupedByAttributes,collectByAttrs,defaultCollectType,name);
    }

    public List<TableResponse> createTables(PortfolioList portfolioList) {
        if(attrNames==null||attrNames.isEmpty()) return Collections.emptyList();
        System.out.println("Table attr list: "+String.join("; ",attrNames));
        return Stream.of(attrNames).flatMap(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute-> SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + (humanAttrs.isEmpty() ? "" :  " by "+ (humanAttrs.isEmpty() ? "*BLANK*" : String.join(", ",humanAttrs)));
            Boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault("",false);
            return groupPortfolioListForGivenAttribute(portfolioList,"").map(groupPair-> {
                return createHelper(groupPair.getSecond().getItemList(), attrList, title, groupPair.getFirst(), includeBlank);
            });
        }).collect(Collectors.toList());
    }

    protected TableResponse createHelper(List<Item> data, List<String> attrList, String yTitle, String subTitle, boolean includeBlanks) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle + (subTitle!=null&&subTitle.length()>0 ? (" (Grouped by "+subTitle+")") : "");
        response.headers = new ArrayList<>();
        response.headers.addAll(attrList);
        response.headers.add(collectorType.toString());
        response.numericAttrNames = Collections.singleton(collectorType.toString());
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                if(data.size()==0) return Collections.emptyList();

                List<Pair<Item,DeepList<Object>>> items = groupTableData(data,attrList);

                if(items.isEmpty()) return Collections.emptyList();

                System.out.println("Starting to group table...");
                Collector<Pair<Item,DeepList<Object>>,?,? extends Number> collector = getCollectorFromCollectorType();

                return collectData(items,attrList,collectorType,collector,includeBlanks);
            }
        };
        response.computeAttributesTask.fork();
        return response;
    }

}
