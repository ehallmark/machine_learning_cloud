package test;

import com.google.gson.Gson;
import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Evan on 8/18/2017.
 */
public class SalesforceElasticSearchQuery {
    public static void main(String[] args) throws Exception {
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        AssetToAssigneeMap assetToAssigneeMap = new AssetToAssigneeMap();
        AtomicLong cnt = new AtomicLong(0);
        AbstractFilter filter = new AbstractFilter(null,null) {
            @Override
            public QueryBuilder getFilterQuery() {
                return QueryBuilders.termQuery(Constants.DOC_TYPE, "applications");
            }

            @Override
            public Tag getOptionsTag() {
                return null;
            }

            @Override
            public void extractRelevantInformationFromParams(Request params) {

            }
        };
        Map<String,NestedAttribute> nestedAttributeMap = new HashMap<>();
        nestedAttributeMap.put(Constants.ASSIGNEES,new AssigneesNestedAttribute());
        nestedAttributeMap.put(Constants.LATEST_ASSIGNEE, new LatestAssigneeNestedAttribute());
        Collection<AbstractAttribute> attributes = Arrays.asList(new AssetNumberAttribute(), new FilingDateAttribute(), new PublicationDateAttribute(), new AssigneesNestedAttribute(), new LatestAssigneeNestedAttribute());
        BufferedWriter writer = new BufferedWriter(new FileWriter("data/all-applications-and-dates.csv"));
        writer.write("asset_number,filing_date,publication_date,assignee\n");
        DataSearcher.searchForAssets(attributes,Arrays.asList(filter), Constants.NAME, SortOrder.ASC, 5000000, nestedAttributeMap, item -> {
            Object name = item.getData(Constants.NAME);
            Object filingDate = item.getData(Constants.FILING_DATE);
            Object pubDate = item.getData(Constants.PUBLICATION_DATE);


            Object originalAssignee = item.getData(Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE);
            if(originalAssignee==null) {
                String filing = assetToFilingMap.getApplicationDataMap().get(name);
                if(filing!=null) {
                    String patent = filingToAssetMap.getPatentDataMap().get(filing);
                    if(patent!=null) {
                        originalAssignee = assetToAssigneeMap.getPatentDataMap().get(patent);
                    }
                }
                if(originalAssignee==null) {
                    originalAssignee = "";
                }
            }

            if(name==null||filingDate==null||pubDate==null) return null;
            try {
                writer.write(name.toString() + "," + filingDate.toString() + "," + pubDate.toString() + ","+originalAssignee.toString().replace(",","")+"\n");
                if(cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished: "+cnt.get());
                    writer.flush();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        },false);

        writer.flush();
        writer.close();
    }
}
