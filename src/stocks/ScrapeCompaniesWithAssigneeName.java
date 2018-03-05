package stocks;

import com.googlecode.concurrenttrees.radix.RadixTree;
import info.debatty.java.stringsimilarity.JaroWinkler;
import models.assignee.normalization.name_correction.AssigneeTrimmer;
import models.assignee.normalization.name_correction.NormalizeAssignees;
import org.nd4j.linalg.primitives.PairBackup;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompaniesWithAssigneeName {
    private static final File csvFile1 = new File(Constants.DATA_FOLDER+"yahoocompanylist.csv");
    private static final File csvFile2 = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");
    //private static final File assigneeToStockPriceOverTimeMapFile = new File(Constants.DATA_FOLDER+"assignee_to_stock_prices_over_time_map.jobj");

    public static void main(String[] args) throws IOException {
        run(csvFile1, new File("data/yahoo_assignees_local.csv"));
        run(csvFile2, new File("data/yahoo_assignees_intl.csv"));
    }

    public static void run(File csvFile, File outputFile) throws IOException {
        final int startYear = 2005;
        final int endYear = LocalDate.now().getYear()-1;
        BufferedReader reader = new BufferedReader(new FileReader(csvFile));

        RadixTree<String> trie = Database.getAssigneePrefixTrie();

        JaroWinkler distance = new JaroWinkler();
        NormalizeAssignees normalizer = new NormalizeAssignees();

        AtomicInteger idx = new AtomicInteger(0);
        final Map<String,Set<String>> companyToExchangeMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Set<String>> companyToTickersMap = reader.lines().parallel().map(line->{
            if(idx.getAndIncrement()%10000==9999) {
                System.out.println("Read: "+idx.get());
            }
            String[] cells = line.split(",");
            if(cells.length<3) return null;
            String symbol = cells[0];
            String company = cells[1];
            if(symbol!=null) symbol = symbol.trim();
            if(company!=null) company = AssigneeTrimmer.standardizedAssignee(company);
            if(symbol.isEmpty()||company==null||company.isEmpty()||!trie.getValuesForClosestKeys(company).iterator().hasNext()) {
                return null;
            }
            String exchange = cells[2];
            final String normalizedCompany = normalizer.normalizedAssignee(company);
            List<String> possible = new ArrayList<>();
            trie.getValuesForClosestKeys(company).forEach(a->possible.add(a));
            PairBackup<String,Double> companyScorePair = possible.stream()
                    .map(p->normalizer.normalizedAssignee(p))
                    .distinct()
                    .filter(p->Math.max(Database.getNormalizedAssetCountFor(p),Database.getAssetCountFor(p))>=100)
                    .map(p->new PairBackup<>(p,distance.similarity(p,normalizedCompany)))
                    .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).findFirst().orElse(null);
            if(companyScorePair==null) return null;
            double score = companyScorePair.getSecond();
            String assignee = companyScorePair.getFirst();
            if(score>=0.93 && Math.min(normalizedCompany.length(),assignee.length()) > 3) {
                if(exchange!=null) {
                    synchronized (companyToExchangeMap) {
                        companyToExchangeMap.putIfAbsent(assignee, Collections.synchronizedSet(new HashSet<>()));
                        companyToExchangeMap.get(assignee).add(exchange);
                    }
                }
                return new PairBackup<>(assignee, symbol);
            } else return null;

        }).filter(p->p!=null).collect(Collectors.groupingBy(p->p.getFirst(),Collectors.mapping(p->p.getSecond(),Collectors.toSet())));

        List<String> companies = new ArrayList<>(companyToTickersMap.keySet());
        companies.forEach(c->{
            String n = normalizer.normalizedAssignee(c);
            if(!c.equals(n)) {
                if(companyToTickersMap.containsKey(n)) {
                    companyToTickersMap.remove(c);
                    companyToExchangeMap.remove(c);
                }
            }
        });

        System.out.println("Companies found: "+companyToTickersMap.size());

        // no pull stock info
        final long to = System.currentTimeMillis()/1000;
        final long from = LocalDateTime.of(startYear,1,1,0,0).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()/1000;

        Map<String,List<PairBackup<LocalDate,Double>>> assigneeToStockPriceOverTimeMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger cnt = new AtomicInteger(0);
        companyToTickersMap.entrySet().parallelStream().forEach(e->{
            System.out.println(""+cnt.getAndIncrement()+" / "+companyToTickersMap.size());
            try {
                List<PairBackup<LocalDate, Double>> data = stockDataFor(e.getValue(), from, to);
                if (data != null) {
                    assigneeToStockPriceOverTimeMap.put(e.getKey(), data);
                }
            } catch(Exception e2) {

            }
        });

        System.out.println("Num assignees with stock prices: "+assigneeToStockPriceOverTimeMap.size());

        // find ai values
        OverallEvaluator evaluator = new OverallEvaluator(false);

        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        StringJoiner csvJoiner = new StringJoiner("\",\"","\"","\"\n");
        csvJoiner.add("Assignee").add("Ticker(s)").add("Stock Exchange(s)").add("Primary WIPO").add("Secondary WIPO").add("Portfolio Size").add("Average AI Value").add("Is Normalized");
        for(int i = startYear; i <= endYear; i++) {
            csvJoiner.add("Percent Gain ("+i+")");
        }
        bw.write(csvJoiner.toString());
        for (String assignee : assigneeToStockPriceOverTimeMap.keySet()) {
            Set<String> tickers = companyToTickersMap.get(assignee);
            StringJoiner priceJoiner = new StringJoiner(",","","\n");
            List<PairBackup<LocalDate,Double>> stocks = assigneeToStockPriceOverTimeMap.get(assignee);
            for(int i = startYear; i <= endYear; i++) {
                // prices
                final int year = i;
                double startingPrice = stocks.stream().filter(p->p.getFirst().getYear()==year&&p.getFirst().getMonth().equals(Month.JANUARY)).mapToDouble(p->p.getSecond()).findFirst().orElse(Double.NaN);
                double endPrice = stocks.stream().filter(p->p.getFirst().getYear()==year+1&&p.getFirst().getMonth().equals(Month.JANUARY)).mapToDouble(p->p.getSecond()).findFirst().orElse(Double.NaN);
                if(Double.isNaN(startingPrice)||Double.isNaN(endPrice)) {
                    priceJoiner.add("NaN");
                } else {
                    if (startingPrice > 0 && endPrice > 0) {
                        priceJoiner.add(String.valueOf((endPrice-startingPrice)/startingPrice));
                    } else {
                        break; // SOMETHING IS FUNKY
                    }
                }
            }
            bw.write(getExcelRow(assignee,tickers,companyToExchangeMap.getOrDefault(assignee,new HashSet<>()),normalizer,evaluator)+","+priceJoiner.toString());
        }
        bw.flush();
        bw.close();

        // Save
        //Database.trySaveObject(assigneeToStockPriceOverTimeMap,assigneeToStockPriceOverTimeMapFile);
    }

    private static final String getExcelRow(String assignee, Set<String> tickers, Set<String> exchanges, NormalizeAssignees normalizer, OverallEvaluator evaluator) {
        int portfolioSize = Math.max(Database.getNormalizedAssetCountFor(assignee),Database.getAssetCountFor(assignee));
        boolean isNormalized = assignee.equals(normalizer.normalizedAssignee(assignee));
        List<String> allAssets = Stream.of(
                Database.selectApplicationNumbersFromExactNormalizedAssignee(assignee),
                Database.selectPatentNumbersFromExactNormalizedAssignee(assignee),
                Database.selectPatentNumbersFromExactAssignee(assignee),
                Database.selectApplicationNumbersFromExactAssignee(assignee))
                .flatMap(list->list.stream()).distinct().collect(Collectors.toList());

        double[] aIValues = allAssets.stream().map(asset->evaluator.getApplicationDataMap().getOrDefault(asset,evaluator.getPatentDataMap().get(asset)))
                .filter(d->d!=null).mapToDouble(n->n.doubleValue()).toArray();

        String aiValue;
        if(aIValues.length>30) {
            aiValue = String.valueOf(DoubleStream.of(aIValues).average().getAsDouble());
        } else {
            aiValue = "N/A";
        }
        // wipo
        String wipoTechnology1;
        String wipoTechnology2;
        WIPOTechnologyAttribute wipoAttr = new WIPOTechnologyAttribute();
        List<String> allWipos =  allAssets.stream().map(asset->wipoAttr.attributesFor(Collections.singleton(asset),1,null))
                .filter(tech->tech!=null)
                .collect(Collectors.groupingBy(t->t,Collectors.counting()))
                .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(2).map(e->e.getKey()).collect(Collectors.toList());
        if(allWipos.size()==2) {
            wipoTechnology1 = allWipos.get(0);
            wipoTechnology2 = allWipos.get(1);
        } else if(allWipos.size()==1) {
            wipoTechnology1 = allWipos.get(0);
            wipoTechnology2 = "";
        } else {
            wipoTechnology1 = "";
            wipoTechnology2 = "";
        }
        return "\""+assignee+"\",\""+String.join("; ",tickers)+"\",\""+String.join("; ",exchanges)+"\",\""+wipoTechnology1+"\",\""+wipoTechnology2+"\","+portfolioSize+","+aiValue+","+isNormalized;
    }

    public static Map<String,List<PairBackup<LocalDate,Double>>> getAssigneeToStockPriceOverTimeMap() {
        //return (Map<String,List<Pair<LocalDate,Double>>>)Database.tryLoadObject(assigneeToStockPriceOverTimeMapFile);
        return null;
    }

    private static List<PairBackup<LocalDate,Double>> stockDataFor(Set<String> symbols, long from, long to) throws Exception {
        // find best symbol
        List<PairBackup<LocalDate,Double>> data = null;
        List<String> sortedSymbols = new ArrayList<>(symbols);
        Collections.sort(sortedSymbols,(s1,s2)->Integer.compare(s1.length(),s2.length()));
        for(String symbol : sortedSymbols) {
            List<PairBackup<LocalDate,Double>> tmp = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
            if(tmp==null) continue;
            if(data==null || tmp.size()>data.size()) data = tmp;
        }
        return data;
    }
}