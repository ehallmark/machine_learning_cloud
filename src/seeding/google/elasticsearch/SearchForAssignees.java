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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
                "\"at&t\" | att | bellsouth | \"bell labs\"",
                "directv",
                "comcast",
                "\"universal pictures\"",
                "facebook",
                "netflix",
                "\"time warner cable\"",
                "\"cox communications\""
        };

        Map<String,Map<String,Map<Integer,Set<String>>>> companyToCountryToFamilyIdsToYearMap = Collections.synchronizedMap(new HashMap<>());
        final BufferedWriter writer = new BufferedWriter(new FileWriter(new File("disney_assignee_foreign.csv")));
        writer.write("Asset Number, Country, Family ID, Priority Date (est.), Assignee(s), Search Term\n");
        for(String assignee : assignees) {
            NestedAttribute assigneeAttr = new AssigneeHarmonized();
            Map<String,Map<Integer,Set<String>>> countryToFamilyIdsToYearMap = Collections.synchronizedMap(new HashMap<>());
            companyToCountryToFamilyIdsToYearMap.put(assignee,countryToFamilyIdsToYearMap);

            AbstractAttribute assigneeText = assigneeAttr.getAttributes().stream().filter(attr->attr instanceof Name).findFirst().orElse(null);

            AdvancedKeywordFilter assigneeFilter = new AdvancedKeywordFilter(assigneeText, AbstractFilter.FilterType.AdvancedKeyword);
            assigneeFilter.setQueryStr(assignee);

            AbstractNestedFilter filter = new AbstractNestedFilter(assigneeAttr,true,assigneeFilter);
            filter.setFilterSubset(Arrays.asList(assigneeFilter));

            ItemTransformer transformer = item -> {
                String familyId = (String) item.getDataMap().get(Constants.FAMILY_ID);
                String year = (String) item.getDataMap().getOrDefault(Constants.PRIORITY_DATE, item.getDataMap().get(Constants.FILING_DATE));
                String country = (String) item.getDataMap().get(Constants.COUNTRY_CODE);
                if(year==null||familyId==null||country==null) return null;
                LocalDate date = LocalDate.parse(year,DateTimeFormatter.BASIC_ISO_DATE);
                year = String.valueOf(date.getYear());
                synchronized (countryToFamilyIdsToYearMap) {
                    countryToFamilyIdsToYearMap.putIfAbsent(country, Collections.synchronizedMap(new HashMap<>()));
                    Map<Integer,Set<String>> familyIdToYearMap = countryToFamilyIdsToYearMap.get(country);
                    familyIdToYearMap.putIfAbsent(Integer.valueOf(year),Collections.synchronizedSet(new HashSet<>()));
                    familyIdToYearMap.get(Integer.valueOf(year)).add(familyId);
                }
                String assigneesMatched = String.join("; ",((String)item.getDataMap().getOrDefault(Constants.ASSIGNEE_HARMONIZED+"."+Constants.NAME,"")).split(DataSearcher.ARRAY_SEPARATOR));
                System.out.println("Assignees matched: "+assigneesMatched);
                StringJoiner sj = new StringJoiner("\",\"", "\"", "\"\n")
                        .add((String)item.getDataMap().get(Constants.FULL_PUBLICATION_NUMBER))
                        .add(country)
                        .add(familyId)
                        .add(date.format(DateTimeFormatter.ISO_DATE))
                        .add(assigneesMatched)
                        .add(assignee);
                synchronized (writer) {
                    try {
                        writer.write(sj.toString());
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            };

            System.out.println("Starting to search for: "+assignee);

            DataSearcher.searchForAssets(index, type, attributes, Collections.singleton(filter), seeding.Constants.NO_SORT, SortOrder.ASC, 100000000, nestedAttributeMap, transformer, false, false, true);

        }
        writer.flush();
        writer.close();


        final BufferedWriter csv = new BufferedWriter(new FileWriter(new File("disney_assignee_foreign_grouped.csv")));
        csv.write("Search,Country,Year,Family Count\n");
        companyToCountryToFamilyIdsToYearMap.forEach((company,countryToFamilyIdsToYearMap)->{
            countryToFamilyIdsToYearMap.forEach((country,yearToFamilyIdsMap)-> {
                yearToFamilyIdsMap.forEach((year, familyIds) -> {
                    try {
                        StringJoiner sj = new StringJoiner("\",\"", "\"", "\"\n");
                        sj.add(company).add(country).add(year.toString()).add(String.valueOf(familyIds.size()));
                        String line = sj.toString();
                        System.out.println("Line: "+line);
                        csv.write(line);
                    }catch(Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        });

        csv.flush();
        csv.close();
    }
}
