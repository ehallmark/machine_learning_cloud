package scrape_patexia;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpBasicAuth {

    public static void downloadFileWithAuth(URL url, OutputStream out) {
        try {
            //String authStr = user + ":" + pass;
            //String authEncoded = Base64.getEncoder().encodeToString(authStr.getBytes());

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            //connection.setDoOutput(true);
            //connection.setRequestProperty("Authorization", "Basic " + authEncoded);

            InputStream in = (InputStream) connection.getInputStream();
            for (int b; (b = in.read()) != -1;) {
                out.write(b);
            }
            out.flush();
            in.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}