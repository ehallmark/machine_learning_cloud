package stocks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeYahooStockPrices {

    public static String getStocksFromSymbol(String symbol) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlFromSymbol(symbol));
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

    private static String urlFromSymbol(String symbol) {
       // return "https://query1.finance.yahoo.com/v7/finance/download/"+symbol+"?period1=1092898800&period2=1510819200&interval=1mo&events=history";
        return "https://finance.yahoo.com/quote/"+symbol+"/history?period1=1092898800&period2=1510819200&interval=1mo&filter=history&frequency=1mo";
    }

    public static void main(String[] args) throws Exception {
        //test
        System.out.println(getStocksFromSymbol("GOOG"));
    }
}
