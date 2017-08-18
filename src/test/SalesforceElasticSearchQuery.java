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
import user_interface.ui_models.attributes.DocKindAttribute;
import user_interface.ui_models.attributes.ResultTypeAttribute;
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
        Collection<String> attributes = Arrays.asList(Constants.NAME,Constants.FILING_DATE,Constants.PUBLICATION_DATE,Constants.ASSIGNEES, Constants.LATEST_ASSIGNEE);
        BufferedWriter writer = new BufferedWriter(new FileWriter("data/all-applications-and-dates.csv"));
        writer.write("asset_number,filing_date,publication_date,original_assignee,latest_assignee\n");
        DataSearcher.searchForAssets(attributes,Arrays.asList(filter),null, Constants.NAME, SortOrder.ASC, 5000000, new HashMap<>(), item -> {
            Object name = item.getData(Constants.NAME);
            Object filingDate = item.getData(Constants.FILING_DATE);
            Object pubDate = item.getData(Constants.PUBLICATION_DATE);
            Object latestAssignees = item.getData(Constants.LATEST_ASSIGNEE);
            Object latestAssignee;
            //System.out.println(new Gson().toJson(latestAssignees));
            if(latestAssignees ==null || !(latestAssignees instanceof List)) latestAssignee="";
            else latestAssignee = ((List)latestAssignees).stream().map(_map->{
                Map<String,Object> map = (Map<String,Object>)_map;
                return map.get(Constants.ASSIGNEE);
            }).filter(a->a!=null).findAny().orElse("");

            Object originalAssignees = item.getData(Constants.ASSIGNEES);
            Object originalAssignee;
            if(originalAssignees ==null || !(originalAssignees instanceof List)) originalAssignee="";
            else originalAssignee = ((List)originalAssignees).stream().map(_map->{
                Map<String,Object> map = (Map<String,Object>)_map;
                return map.get(Constants.ASSIGNEE);
            }).filter(a->a!=null).findAny().orElse("");

            if(name==null||filingDate==null||pubDate==null||latestAssignee.toString().isEmpty()) return null;
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
