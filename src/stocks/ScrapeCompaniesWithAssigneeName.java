package stocks;

import com.googlecode.concurrenttrees.radix.RadixTree;
import info.debatty.java.stringsimilarity.JaroWinkler;
import models.assignee.normalization.name_correction.AssigneeTrimmer;
import models.assignee.normalization.name_correction.NormalizeAssignees;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompaniesWithAssigneeName {
    private static final File csvFile = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");
    private static final File assigneeToStockPriceOverTimeMapFile = new File(Constants.DATA_FOLDER+"assignee_to_stock_prices_over_time_map.jobj");

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(csvFile));

        RadixTree<String> trie = Database.getNormalizedAssigneePrefixTrie();
        JaroWinkler distance = new JaroWinkler();

        NormalizeAssignees normalizer = new NormalizeAssignees();

        AtomicInteger idx = new AtomicInteger(0);
        Map<String,Set<String>> companyToTickersMap = reader.lines().parallel().map(line->{
            if(idx.getAndIncrement()%10000==9999) {
                System.out.println("Read: "+idx.get());
            }
            String[] cells = line.split(",");
            if(cells.length<2) return null;
            String symbol = cells[0];
            String company = cells[1];
            if(symbol!=null) symbol = symbol.trim();
            if(company!=null) company = normalizer.normalizedAssignee(AssigneeTrimmer.standardizedAssignee(company));
            if(symbol.isEmpty()||company==null||company.isEmpty()||!trie.getValuesForClosestKeys(company).iterator().hasNext()) {
                return null;
            }
            final String normalizedCompany = company;
            List<String> possible = new ArrayList<>();
            trie.getValuesForClosestKeys(company).forEach(a->possible.add(a));
            Pair<String,Double> companyScorePair = possible.stream()
                    .map(p->new Pair<>(p,distance.similarity(p,normalizedCompany)))
                    .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).findFirst().get();

            double score = companyScorePair.getSecond();
            String assignee = companyScorePair.getFirst();
            if(score>=0.9 && Math.min(normalizedCompany.length(),assignee.length()) > 3) {
                return new Pair<>(assignee, symbol);
            } else return null;
        }).filter(p->p!=null).collect(Collectors.groupingBy(p->p.getFirst(),Collectors.mapping(p->p.getSecond(),Collectors.toSet())));

        System.out.println("Companies found: "+companyToTickersMap.size());

        // no pull stock info
        final long to = System.currentTimeMillis()/1000;
        final long from = LocalDateTime.of(2005,1,1,0,0).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()/1000;

        Map<String,List<Pair<LocalDate,Double>>> assigneeToStockPriceOverTimeMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger cnt = new AtomicInteger(0);
        companyToTickersMap.entrySet().parallelStream().forEach(e->{
            System.out.println(""+cnt.getAndIncrement()+" / "+companyToTickersMap.size());
            try {
                List<Pair<LocalDate, Double>> data = stockDataFor(e.getValue(), from, to);
                if (data != null) {
                    assigneeToStockPriceOverTimeMap.put(e.getKey(), data);
                }
            } catch(Exception e2) {

            }
        });

        System.out.println("Num assignees with stock prices: "+assigneeToStockPriceOverTimeMap.size());
        // Save
        Database.trySaveObject(assigneeToStockPriceOverTimeMap,assigneeToStockPriceOverTimeMapFile);
    }

    public static Map<String,List<Pair<LocalDate,Double>>> getAssigneeToStockPriceOverTimeMap() {
        return (Map<String,List<Pair<LocalDate,Double>>>)Database.tryLoadObject(assigneeToStockPriceOverTimeMapFile);
    }

    private static List<Pair<LocalDate,Double>> stockDataFor(Set<String> symbols, long from, long to) throws Exception {
        // find best symbol
        List<Pair<LocalDate,Double>> data = null;
        List<String> sortedSymbols = new ArrayList<>(symbols);
        Collections.sort(sortedSymbols,(s1,s2)->Integer.compare(s1.length(),s2.length()));
        for(String symbol : sortedSymbols) {
            List<Pair<LocalDate,Double>> tmp = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
            if(tmp==null) continue;
            if(data==null || tmp.size()>data.size()) data = tmp;
        }
        return data;
    }
}