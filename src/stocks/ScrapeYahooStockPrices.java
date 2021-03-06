package stocks;

import org.nd4j.linalg.primitives.Pair;
import stocks.util.StockResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeYahooStockPrices {

    public static List<Pair<LocalDate,Double>> getStocksFromSymbols(String symbol, long from, long to) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlFromSymbol(symbol,from,to));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        String json = result.toString();

        try {
            StockResponse response = new StockResponse(json);
            response.parse();

            if(response.getDates()==null||response.getPrices()==null) return null;

            List<Pair<LocalDate,Double>> data = new ArrayList<>();
            for(int i = 0; i < Math.min(response.getDates().size(),response.getPrices().size()); i++) {
                if(response.getPrices().get(i)==null||response.getDates().get(i)==null) continue;

                LocalDate date =
                        Instant.ofEpochMilli(response.getDates().get(i).longValue()*1000).atZone(ZoneId.of("America/Los_Angeles")).toLocalDate();

                Pair<LocalDate,Double> record = new Pair<>(date,response.getPrices().get(i));
                data.add(record);
            }
            if(data.isEmpty()) return null;
            return data;

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error on: "+symbol);
            return null;
        }
    }

    private static String urlFromSymbol(String symbol, long from, long to) {
       // return "https://query1.finance.yahoo.com/v7/finance/download/"+symbol+"?period1=1092898800&period2=1510819200&interval=1mo&events=history";
       // return "https://finance.yahoo.com/quote/"+symbol+"/history?period1=1092898800&period2=1510819200&interval=1mo&filter=history&frequency=1mo";
        String url = "https://query2.finance.yahoo.com/v8/finance/chart/"+symbol+"?formatted=true&crumb=6iPfwrHM.4i&lang=en-IN&region=IN&period1="+from+"&period2="+to+"&interval=1d&events=div|split&corsDomain=in.finance.yahoo.com";
        //System.out.println("Searching for url: "+url);
        return url;
    }


    public static long dateToLong(LocalDate date) {
        return date.atStartOfDay().plusHours(12).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()/1000;
    }

    public static void main(String[] args) throws Exception {
        // test
        long to = dateToLong(LocalDate.now().plusDays(1));
        long from = dateToLong(LocalDate.now().minusMonths(2));

        //test
        System.out.println(getStocksFromSymbols("KR",from,to));
    }
}
