package stocks;

import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import stocks.util.Stock;

import java.io.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompanyTickers {
    private static final File csvFile = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");

    public static void main(String[] args) throws IOException {
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
        tickerToNameMap.entrySet().parallelStream().forEach(e->{
            String ticker = e.getKey();
            String company = e.getValue();
            //System.out.println(""+cnt.getAndIncrement()+" / "+tickers.size());
            try {
                List<Pair<LocalDate, Double>> data = stockDataFor(ticker, from, to);
                if (data != null && data.size()>14 && data.get(data.size()-1).getFirst().isAfter(today.minusDays(2))) {
                    double lastPrice = data.get(data.size()-1).getSecond();
                    // don't want penny stocks
                    if(lastPrice>2d) {
                        double[] prices = data.stream().limit(data.size()-1).mapToDouble(p->p.getSecond()).toArray();
                        Stock stock = new Stock(ticker,today,prices);
                        stock.nextTimeStep(data.get(data.size()-1).getSecond());
                        double score = stock.getVarianceNormalized()
                                *(stock.getAverageReturn1()<stock.getAverageReturn10()?1.0:0.0)
                                *(Math.expm1(Math.abs(stock.getHistoricalVelocities()[stock.getHistoricalVelocities().length-1])))
                                *Math.max(0,stock.getAverageAcceleration()+stock.getHistoricalAccelerations()[stock.getHistoricalAccelerations().length-1]);
                        if(score>0) {
                            System.out.println("Score for "+company+": "+score);
                            System.out.println("Stock: "+stock.toString());
                        }
                        //System.out.println(stock.toString());
                    }
                }
            } catch(Exception e2) {
                if(!(e2 instanceof FileNotFoundException)) e2.printStackTrace();
            }
        });
    }

    private static List<Pair<LocalDate,Double>> stockDataFor(String symbol, long from, long to) throws Exception {
        // find best symbol
        List<Pair<LocalDate,Double>> data = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
        return data;
    }
}
