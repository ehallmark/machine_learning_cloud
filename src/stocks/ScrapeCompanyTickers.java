package stocks;

import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
        final long to = ScrapeYahooStockPrices.dateToLong(LocalDate.now().plusDays(1));
        final long from = ScrapeYahooStockPrices.dateToLong(LocalDate.now().minusMonths(1));
        tickerToNameMap.entrySet().parallelStream().forEach(e->{
            String ticker = e.getKey();
            String company = e.getValue();
            //System.out.println(""+cnt.getAndIncrement()+" / "+tickers.size());
            try {
                List<Pair<LocalDate, Double>> data = stockDataFor(ticker, from, to);
                if (data != null && data.size()>10 && data.get(data.size()-1).getFirst().equals(LocalDate.now())) {
                    double lastPrice = data.get(data.size()-1).getSecond();
                    // don't want penny stocks
                    if(lastPrice>2d) {
                        double lastLastPrice = data.get(data.size() - 2).getSecond();
                        double lastLastLastPrice = data.get(data.size() - 3).getSecond();

                        double averageIncreaseRecent = 0.5 * (lastPrice - lastLastPrice) / lastPrice + 0.5 * (lastLastPrice - lastLastLastPrice) / lastLastPrice;
                        double averageIncreaseOld = 0d;
                        for (int i = 0; i < data.size() - 4; i++) {
                            double d1 = data.get(i).getSecond();
                            double d2 = data.get(i + 1).getSecond();
                            averageIncreaseOld += (d2 - d1) / d1;
                        }
                        averageIncreaseOld /= data.size() - 4;

                        if (averageIncreaseOld > 0.01 && averageIncreaseRecent < 0.05) {
                            System.out.println();
                            System.out.println("Found candidate: " + ticker + " -> " + company);
                        } else {
                            System.out.print("-");
                        }
                    }
                }
            } catch(Exception e2) {

            }
        });
    }

    private static List<Pair<LocalDate,Double>> stockDataFor(String symbol, long from, long to) throws Exception {
        // find best symbol
        List<Pair<LocalDate,Double>> data = ScrapeYahooStockPrices.getStocksFromSymbols(symbol, from, to);
        return data;
    }
}
