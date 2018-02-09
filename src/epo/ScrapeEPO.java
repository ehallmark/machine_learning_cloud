package epo;

import org.nd4j.linalg.primitives.Pair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeEPO {

    public static List<Pair<LocalDate,Double>> getFamilyMembersForAsset(String asset, String code) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL("http://ops.epo.org/3.2/rest-services/family/publication/DOCDB");
        System.out.println(url.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        conn.setRequestProperty("Content-Type","text/plain");
        conn.setRequestProperty("Content-Length", Integer.toString(2+asset.length()));
        conn.setRequestProperty("Authorization", "Bearer cTN9PpVHLgLlIRceVKLO4BRWKnRA");
        conn.setRequestMethod("GET");

        conn.setDoOutput(true);
        conn.getOutputStream().write(("US"+asset).getBytes("UTF8"));


        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        String json = result.toString();

        System.out.println("Result: "+result.toString());

        return null;
    }


    public static void main(String[] args) throws Exception {

        //test
        System.out.println(getFamilyMembersForAsset("2004201546","A1"));
    }
}
