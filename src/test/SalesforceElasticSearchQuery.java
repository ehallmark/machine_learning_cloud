package test;

import elasticsearch.DataSearcher;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.ui_models.attributes.DocKindAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Evan on 8/18/2017.
 */
public class SalesforceElasticSearchQuery {
    public static void main(String[] args) throws Exception {
        AtomicLong cnt = new AtomicLong(0);
        AbstractFilter filter = new AbstractIncludeFilter(new DocKindAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, Arrays.asList("A1","A2"));
        Collection<String> attributes = Arrays.asList(Constants.NAME,Constants.FILING_DATE,Constants.PUBLICATION_DATE);
        BufferedWriter writer = new BufferedWriter(new FileWriter("data/all-applications-and-dates.csv"));
        writer.write("asset_number,filing_date,publication_date\n");
        DataSearcher.searchForAssets(attributes,Arrays.asList(filter),null, Constants.NAME, SortOrder.ASC, 5000000, new HashMap<>(), item -> {
            Object name = item.getData(Constants.NAME);
            Object filingDate = item.getData(Constants.FILING_DATE);
            Object pubDate = item.getData(Constants.PUBLICATION_DATE);
            if(name==null||filingDate==null||pubDate==null) return null;
            try {
                writer.write(name.toString() + "," + filingDate.toString() + "," + pubDate.toString() + "\n");
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
