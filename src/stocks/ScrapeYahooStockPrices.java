package stocks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeYahooStockPrices {

    public static String getStocksFromSymbols(String symbol) throws Exception {
        long to = System.currentTimeMillis()/1000;
        long from = LocalDateTime.now().minusYears(5).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()/1000;

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
        return result.toString();
    }

    private static String urlFromSymbol(String symbol, long from, long to) {
       // return "https://query1.finance.yahoo.com/v7/finance/download/"+symbol+"?period1=1092898800&period2=1510819200&interval=1mo&events=history";
       // return "https://finance.yahoo.com/quote/"+symbol+"/history?period1=1092898800&period2=1510819200&interval=1mo&filter=history&frequency=1mo";
        String url = "https://query2.finance.yahoo.com/v8/finance/chart/"+symbol+"?formatted=true&crumb=6iPfwrHM.4i&lang=en-IN&region=IN&period1="+from+"&period2="+to+"&interval=1mo&events=div|split&corsDomain=in.finance.yahoo.com";
        System.out.println("Searching for urL: "+url);
        return url;
    }

    public static void main(String[] args) throws Exception {
        //test
        System.out.println(getStocksFromSymbols("GOOG"));
    }
}
