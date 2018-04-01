package scrape_patexia;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.StringJoiner;

public class HttpBasicAuth {

    public static String downloadFileWithAuth(URL url) {
        StringJoiner sj = new StringJoiner("");
        try {
            //String authStr = user + ":" + pass;
            //String authEncoded = Base64.getEncoder().encodeToString(authStr.getBytes());

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            //connection.setDoOutput(true);
            //connection.setRequestProperty("Authorization", "Basic " + authEncoded);

            InputStream in = (InputStream) connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(line->{
                sj.add(line);
            });
            in.close();
            reader.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return sj.toString();
    }
}