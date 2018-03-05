package stocks;

import elasticsearch.DataSearcher;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.primitives.PairBackup;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.FilingDateAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/17/17.
 */
public class CreateAssetToFilingDateMap {
    private static final File assetToFilingDateMapFile = new File(Constants.DATA_FOLDER+"stock_asset_to_filing_date_map.jobj");
    public static void main(String[] args) throws Exception {
        Map<String,List<PairBackup<LocalDate,Double>>> assigneeToStockPriceOverTimeMap = ScrapeCompanyTickers.getAssigneeToStockPriceOverTimeMap();

        Map<String,Set<String>> assigneeToAssetsMap = getAssigneeToAssetsMap(assigneeToStockPriceOverTimeMap);

        List<String> allAssets = assigneeToAssetsMap.values().parallelStream().flatMap(list->list.stream()).distinct().collect(Collectors.toList());
        System.out.println("Num assets found: "+allAssets);
        
        // pull filing dates from elasticsearch
        Map<String,LocalDate> assetToFilingDateMap = Collections.synchronizedMap(new HashMap<>());
        DataSearcher.searchForAssets(
                Arrays.asList(new FilingDateAttribute()),
                Arrays.asList(new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, allAssets)),
                Constants.NAME,
                SortOrder.ASC,
                allAssets.size(),
                Collections.emptyMap(),
                item -> {
                    Object dateObj = item.getData(Constants.FILING_DATE);
                    if(dateObj!=null) {
                        LocalDate date = LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE);
                        date = date.withDayOfMonth(1);
                        assetToFilingDateMap.put(item.getName(),date);
                    }
                    return null;
                },
                false,
                false,
                false
        );

        System.out.println("Found filing dates for "+assetToFilingDateMap.size()+" out of "+allAssets.size());

        Database.trySaveObject(assetToFilingDateMap,assetToFilingDateMapFile);
    }

    public static Map<String,LocalDate> getAssetToFilingDateMap() {
        if(!assetToFilingDateMapFile.exists()) {
            try {
                main(null);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
        return (Map<String,LocalDate>) Database.tryLoadObject(assetToFilingDateMapFile);
    }

    public static Map<String,Set<String>> getAssigneeToAssetsMap(Map<String,List<PairBackup<LocalDate,Double>>> assigneeToStockPriceOverTimeMap) {
        Map<String,Set<String>> assigneeToAssetsMap = Collections.synchronizedMap(new HashMap<>());
        {
            Map<String, Collection<String>> allAssigneesToAssetsMap = Database.getNormalizedAssigneeToPatentsMap();
            assigneeToStockPriceOverTimeMap.keySet().parallelStream().forEach(assignee -> {
                if (allAssigneesToAssetsMap.containsKey(assignee)) {
                    assigneeToAssetsMap.put(assignee, Collections.synchronizedSet(new HashSet<>(allAssigneesToAssetsMap.get(assignee))));
                }
            });
        }
        return assigneeToAssetsMap;
    }
}
