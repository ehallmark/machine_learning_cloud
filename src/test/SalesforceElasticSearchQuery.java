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
        AtomicLong cnt = new AtomicLong(0);
        AbstractFilter filter = new AbstractFilter(null,null) {
            @Override
            public QueryBuilder getFilterQuery() {
                return QueryBuilders.scriptQuery(new Script(ScriptType.INLINE,"painless","doc['name'].value.length()>8", new HashMap<>()));
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
        Collection<String> attributes = Arrays.asList(Constants.NAME,Constants.FILING_DATE,Constants.PUBLICATION_DATE,Constants.ASSIGNEES, Constants.LATEST_ASSIGNEE,Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE, Constants.ASSIGNEES+"."+Constants.ASSIGNEE);
        BufferedWriter writer = new BufferedWriter(new FileWriter("data/all-applications-and-dates.csv"));
        writer.write("asset_number,filing_date,publication_date,original_assignee,latest_assignee\n");
        DataSearcher.searchForAssets(attributes,Arrays.asList(filter),null, Constants.NAME, SortOrder.ASC, 5000000, nestedAttributeMap, item -> {
            Object name = item.getData(Constants.NAME);
            Object filingDate = item.getData(Constants.FILING_DATE);
            Object pubDate = item.getData(Constants.PUBLICATION_DATE);

            System.out.println("Num attrs found: "+item.getDataMap().size());

            Object latestAssignee = item.getData(Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE);
            Object originalAssignee = item.getData(Constants.ASSIGNEES+"."+Constants.ASSIGNEE);

            if(name==null||filingDate==null||pubDate==null||latestAssignee==null||latestAssignee.toString().isEmpty()) return null;
            try {
                writer.write(name.toString() + "," + filingDate.toString() + "," + pubDate.toString() + ","+originalAssignee.toString().replace(",","")+","+latestAssignee.toString().replace(",","")+"\n");
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
