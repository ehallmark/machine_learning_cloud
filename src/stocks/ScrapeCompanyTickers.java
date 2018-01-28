package stocks;

import models.assignee.normalization.name_correction.AssigneeTrimmer;
import models.assignee.normalization.name_correction.NormalizeAssignees;
import com.googlecode.concurrenttrees.radix.RadixTree;
import info.debatty.java.stringsimilarity.JaroWinkler;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompanyTickers {
    private static final File csvFile = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");
    private static final File assigneeToStockPriceOverTimeMapFile = new File(Constants.DATA_FOLDER+"assignee_to_stock_prices_over_time_map.jobj");
    private static final File assigneeToStockPriceOverTimeMapCSVFile = new File(Constants.DATA_FOLDER+"assignee_to_stock_prices_over_time_map.csv");

    public static void main(String[] args) throws IOException {
        final int startYear = 1970;
        BufferedReader reader = new BufferedReader(new FileReader(csvFile));

        //Set<String> allAssignees = new HashSet<>(Database.getAssignees());

        //RadixTree<String> trie = Database.getNormalizedAssigneePrefixTrie();
        //JaroWinkler distance = new JaroWinkler();

        //NormalizeAssignees normalizer = new NormalizeAssignees();

        AtomicInteger idx = new AtomicInteger(0);
        Set<String> tickers = reader.lines().parallel().map(line->{
            if(idx.getAndIncrement()%10000==9999) {
                System.out.println("Read: "+idx.get());
            }
            String[] cells = line.split(",");
            if(cells.length<2) return null;
            String symbol = cells[0];
            if(symbol!=null) symbol = symbol.trim();
            if(symbol.isEmpty()) {
                return null;
            }

            return symbol;
        }).filter(p->p!=null).collect(Collectors.toSet());

        System.out.println("Companies found: "+tickers.size());

        // no pull stock info
        final long to = System.currentTimeMillis()/1000;
        final long from = LocalDateTime.of(startYear,1,1,0,0).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()/1000;

        Map<String,List<Pair<LocalDate,Double>>> assigneeToStockPriceOverTimeMap = Collections.synchronizedMap(new HashMap<>());

        BufferedWriter writer = new BufferedWriter(new FileWriter(assigneeToStockPriceOverTimeMapCSVFile));
        AtomicInteger cnt = new AtomicInteger(0);
        tickers.parallelStream().forEach(ticker->{
            System.out.println(""+cnt.getAndIncrement()+" / "+tickers.size());
            try {
                List<Pair<LocalDate, Double>> data = stockDataFor(ticker, from, to);
                if (data != null && data.size()>0) {
                    List<String> dataStr = data.stream().map(d->d.getFirst().toString()+":"+d.getSecond()).collect(Collectors.toList());
                    writer.write("\""+ticker+"\","+String.join(",",dataStr)+"\n");
                    assigneeToStockPriceOverTimeMap.put(ticker, data);
                }
            } catch(Exception e2) {

            }
        });
        writer.flush();
        writer.close();

        System.out.println("Num assignees with stock prices: "+assigneeToStockPriceOverTimeMap.size());
        // Save
        Database.trySaveObject(assigneeToStockPriceOverTimeMap,assigneeToStockPriceOverTimeMapFile);
    }

    public static Map<String,List<Pair<LocalDate,Double>>> getAssigneeToStockPriceOverTimeMap() {
        return (Map<String,List<Pair<LocalDate,Double>>>)Database.tryLoadObject(assigneeToStockPriceOverTimeMapFile);
    }

    private static List<Pair<LocalDate,Double>> stockDataFor(String symbol, long from, long to) throws Exception {
        // find best symbol
        List<Pair<LocalDate,Double>> data = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
        return data;
    }
}
