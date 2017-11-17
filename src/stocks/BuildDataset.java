package stocks;

import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/17/17.
 */
public class BuildDataset {
    private static final File csvOutputFile = new File(Constants.DATA_FOLDER+"stock_model.csv");
    public static void main(String[] args) throws Exception {
        Map<String,List<Pair<LocalDate,Double>>> assigneeToStockPriceOverTimeMap = ScrapeCompanyTickers.getAssigneeToStockPriceOverTimeMap();
        Map<String,Set<String>> assigneeToPatentMap = CreateAssetToFilingDateMap.getAssigneeToAssetsMap(assigneeToStockPriceOverTimeMap);
        Map<String,LocalDate> assetToDateMap = CreateAssetToFilingDateMap.getAssetToFilingDateMap();

        BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputFile));

        List<String> allAssignees = new ArrayList<>(assigneeToStockPriceOverTimeMap.keySet());

        List<LocalDate> allDates = new ArrayList<>();
        LocalDate start = LocalDate.of(2004,12,1);
        while(start.isBefore(LocalDate.now())) {
            allDates.add(start);
            start = start.plusMonths(1);
        }

        StringJoiner headers = new StringJoiner(",","","\n");
        headers.add("Assignee");
        allDates.forEach(date->{
            headers.add(date.toString()+"-stockprice");
            headers.add(date.toString()+"-numpatents");
            headers.add(date.toString()+"-patents");
        });
        bw.write(headers.toString());
        for(String assignee : allAssignees) {
            Set<String> assets = assigneeToPatentMap.get(assignee);
            if(assets == null) continue;

            // group assets by month
            Map<LocalDate,Set<String>> dateToAssetsMap = assets.parallelStream()
                    .filter(asset->assetToDateMap.containsKey(asset))
                    .map(asset->new Pair<>(asset,assetToDateMap.get(asset)))
                    .collect(Collectors.groupingBy(p->p.getSecond(),Collectors.mapping(p->p.getFirst(),Collectors.toSet())));

            Map<LocalDate,Double> stockPrices = assigneeToStockPriceOverTimeMap.get(assignee).stream()
                    .collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));

            StringJoiner data = new StringJoiner(",","","\n");
            data.add("\""+assignee+"\"");
            for(int i = 0; i < allDates.size(); i++) {
                LocalDate date = allDates.get(i);
                data.add(String.valueOf(stockPrices.getOrDefault(date,0d)));
                Set<String> monthlyAssets = dateToAssetsMap.getOrDefault(date, Collections.emptySet());
                data.add(String.valueOf(monthlyAssets.size()));
                data.add(String.join("; ",monthlyAssets));
            }
            bw.write(data.toString());
        }

        bw.flush();
        bw.close();
    }
}
