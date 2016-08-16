package seeding;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 8/11/16.
 */
public class SeedCompanyVectors {
    private static final String USER_AGENT = "Mozilla/5.0";

    // HTTP POST request
    private static void sendPost(String candidateSetName, String assignee, List<String> patents) throws Exception {
        assert candidateSetName!=null && !(assignee==null && patents==null);
        String url = "http://192.168.1.148:4567/create";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = null;
        if(patents==null || patents.size() == 0) urlParameters = "name="+candidateSetName+"&assignee="+assignee;
        else if(assignee == null || assignee.trim().length() == 0) urlParameters = "name="+candidateSetName+"&patents="+String.join(" ",patents);
        else throw new RuntimeException("Invalid parameters!");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        System.out.println(response.toString());

    }

    public static void main(String[] args) throws Exception {
        sendPost("Huawei", "huawei", null);
        sendPost("Panasonic", "panasonic", null);
        sendPost("Sony", "sony", null);
        sendPost("ZTE", "zte", null);
        sendPost("Orange", "orange", null);
        sendPost("Cisco", "cisco", null);
        sendPost("Telia Custom", null, Arrays.asList(Constants.CUSTOM_TELIA_PATENT_LIST.split("\\s+")));
        sendPost("Verizon", "verizon", null);
        // ETSI PATENTS!
        sendPost("ETSI", null, Arrays.asList(Constants.ETSI_PATENT_LIST.split("\\s+")));
    }
}
