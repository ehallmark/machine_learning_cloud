package stocks;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import stocks.util.CovarianceMatrix;
import stocks.util.Stock;

import java.io.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompanyTickers {
    private static final File csvFile = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(csvFile));
        AtomicInteger idx = new AtomicInteger(0);
        Map<String,String> tickerToNameMap = reader.lines().parallel().map(line->{
            if(idx.getAndIncrement()%10000==9999) {
                System.out.println("Read: "+idx.get());
            }
            String[] cells = line.split(",");
            if(cells.length<5) return null;
            String symbol = cells[0];
            if(symbol!=null) symbol = symbol.trim();
            if(symbol.isEmpty()||symbol.contains(".")||symbol.contains("-")) {
                return null;
            }
            String country = cells[4];
            if(country==null||!country.equals("USA")) {
                return null;
            }
            String name = cells[1];
            if(name==null||name.trim().isEmpty()) return null;
            return new Pair<>(symbol,name);
        }).filter(p->p!=null).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));

        System.out.println("Companies found: "+tickerToNameMap.size());

        // no pull stock info
        LocalDate today = LocalDate.now();
        final long to = ScrapeYahooStockPrices.dateToLong(today.plusDays(1));
        final long from = ScrapeYahooStockPrices.dateToLong(LocalDate.now().minusMonths(3));
        // known stock
        List<Pair<LocalDate,Double>> googleStock = stockDataFor("GOOG", from, to);
        final LocalDate latestTradingDate = googleStock.get(googleStock.size()-1).getFirst();
        final int numTrades = googleStock.size();
        System.out.println("Latest trading date: "+latestTradingDate.toString());
        System.out.println("Num trades: "+numTrades);
        Map<String,List<Pair<LocalDate,Double>>> dataMap = Collections.synchronizedMap(new HashMap<>());
        tickerToNameMap.entrySet().parallelStream().limit(5000).forEach(e->{
            String ticker = e.getKey();
            String company = e.getValue();
            //System.out.println(""+cnt.getAndIncrement()+" / "+tickers.size());
            try {
                List<Pair<LocalDate, Double>> data = stockDataFor(ticker, from, to);
                if (data != null && data.size()==numTrades && data.get(data.size()-1).getSecond()>3d && data.get(data.size()-1).getFirst().equals(latestTradingDate)) {
                    dataMap.put(ticker, data);
                    if(dataMap.size()%100==99) {
                        System.out.println("Found: "+dataMap.size());
                    }
                }
            } catch(Exception e2) {
                if(!(e2 instanceof FileNotFoundException)) e2.printStackTrace();
            }
        });

        CovarianceMatrix matrix = new CovarianceMatrix(dataMap,numTrades);

        INDArray cov = matrix.getCovMatrix();
        INDArray negativelyCorrelated = Nd4j.argMax(cov.neg(),1);
        int mostNegativelyCorrelated = Nd4j.argMax(cov.min(1).neg().reshape(cov.rows(),1),0).getInt(0);
        int otherIdx = negativelyCorrelated.getInt(mostNegativelyCorrelated);

        System.out.println("most negatively correlated stocks: "+matrix.getCategories().get(mostNegativelyCorrelated)+" and "+matrix.getCategories().get(otherIdx));
    }

    private static List<Pair<LocalDate,Double>> stockDataFor(String symbol, long from, long to) throws Exception {
        // find best symbol
        List<Pair<LocalDate,Double>> data = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
        return data;
    }
}
