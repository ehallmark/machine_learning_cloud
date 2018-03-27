package seeding.google.elasticsearch;

import elasticsearch.DataSearcher;
import org.elasticsearch.search.sort.SortOrder;
import seeding.google.attributes.AssigneeHarmonized;
import seeding.google.attributes.Constants;
import seeding.google.attributes.Name;
import seeding.google.mongo.IngestPatents;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.filters.AdvancedKeywordFilter;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

public class SearchForAssignees {
    public static void main(String[] args) throws Exception {
        String index = IngestPatents.INDEX_NAME;
        String type = IngestPatents.TYPE_NAME;

        Collection<AbstractAttribute> attributes = Constants.buildAttributes();

        Map<String,NestedAttribute> nestedAttributeMap = attributes.stream()
                .filter(attr->attr instanceof NestedAttribute)
                .collect(Collectors.toMap(e->e.getFullName(),e->(NestedAttribute)e));

        final String[] assignees = new String[]{
            "amazon",
            "google",
            "disney",
            "apple",
            "verizon",
            "\"at&t\" | att | bellsouth | \"bell labs\" | directv",
            "comcast | \"cox communications\"",
            "facebook",
            "netflix",
            "\"time warner\""
        };

        final BufferedWriter csv = new BufferedWriter(new FileWriter(new File("disney_assignee_foreign.csv")));

        csv.write("Search,Asset,Family ID,Filing Date,Country");
        for(String assignee : assignees) {
            NestedAttribute assigneeAttr = new AssigneeHarmonized();

            AbstractAttribute assigneeText = assigneeAttr.getAttributes().stream().filter(attr->attr instanceof Name).findFirst().orElse(null);

            AdvancedKeywordFilter assigneeFilter = new AdvancedKeywordFilter(assigneeText, AbstractFilter.FilterType.AdvancedKeyword);
            assigneeFilter.setQueryStr(assignee);

            AbstractNestedFilter filter = new AbstractNestedFilter(assigneeAttr,true,assigneeFilter);
            filter.setFilterSubset(Arrays.asList(assigneeFilter));

            ItemTransformer transformer = item -> {
                String asset = (String) item.getDataMap().getOrDefault(Constants.FULL_PUBLICATION_NUMBER,"");
                String familyId = (String) item.getDataMap().getOrDefault(Constants.FAMILY_ID,"");
                String date = (String) item.getDataMap().getOrDefault(Constants.FILING_DATE,"");
                String country = (String) item.getDataMap().getOrDefault(Constants.COUNTRY_CODE,"");
                try {
                    StringJoiner sj = new StringJoiner("\",\"", "\"", "\"\n");
                    sj.add(assignee).add(asset).add(familyId).add(date).add(country);
                    String line = sj.toString();
                    System.out.println(line);
                    synchronized (csv) {
                        csv.write(line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            };

            System.out.println("Starting to search for: "+assignee);

            DataSearcher.searchForAssets(index, type, attributes, Collections.singleton(filter), seeding.Constants.NO_SORT, SortOrder.ASC, 100000000, nestedAttributeMap, transformer, false, false, true);

            csv.flush();
        }

        csv.close();
    }
}
