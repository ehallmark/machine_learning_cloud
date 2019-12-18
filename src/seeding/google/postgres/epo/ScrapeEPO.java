package seeding.google.postgres.epo;

import com.google.gson.Gson;
import seeding.Database;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeEPO {
    public static final File dataDir = new File("epo_asset_family_maps/");
    static {
        if(!dataDir.exists()) dataDir.mkdirs();
    }
    public static final File assetsSeenFile = new File("epo_asset_to_family_assets_seen_so_far.jobj");

    private static String generateNewAuthToken() throws IOException{
        String key = "AZ42DGb0AeTZ4wwSUWnRoGdGjnP8Gfjc";
        String secret = "O7PG38t4P2uJK2IQ";

        Object auth_token;

        {
            URL url = new URL ("https://ops.epo.org/3.2/auth/accesstoken");
            byte[] bytes = (key+":"+secret).getBytes("UTF-8");
            String encoding = Base64.getUrlEncoder().encodeToString(bytes).trim();

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            //connection.setRequestProperty("grant_type","client_credentials");
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");

            connection.setDoOutput(true);
            connection.getOutputStream().write("grant_type=client_credentials".getBytes("UTF-8"));

            InputStream content = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(content));
            String line;
            StringBuilder result = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
            try {
                auth_token = new Gson().fromJson(result.toString(), Map.class).get("access_token");
            } catch(Exception e) {
                e.printStackTrace();
                auth_token=null;
            }
        }

        if(auth_token==null) {
            throw new NullPointerException("Auth_token is null.");
        }
        System.out.println("Auth token: "+auth_token);
        return (String)auth_token;
    }

    private AtomicInteger cnt = new AtomicInteger(0);
    private String getFamilyMembersForAssetHelper(String asset, String auth_token, ProxyHandler proxyHandler) throws Exception {
        if(auth_token!=null) {
            if(proxyHandler==null) {
                if(asset == null) return null;
                URL url = new URL("http://ops.epo.org/3.2/rest-services/family/publication/docdb/"+asset);
                System.out.println(url);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization","Bearer "+auth_token);
                InputStream content = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(content));
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                rd.close();
                return parseJsonDoc(result.toString());
            } else {
                HttpURLConnection connection = proxyHandler.getProxyUrlForApplication(asset, auth_token);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + auth_token);
                InputStream content = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(content));
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                rd.close();
                return parseJsonDoc(result.toString());
            }
        }
        return null;
    }

    protected String parseJsonDoc(String json) {
        if(cnt.getAndIncrement()%100==99) {
            System.out.print("-");
        }
        if(cnt.get()%1000==0) {
            System.out.println(" Completed: "+cnt.get());
        }
        return json;
    }

    public void scrapeFamilyMembersForAssets(List<String> assets, int maxRetries, BufferedWriter writer, long timeoutMillis, ProxyHandler proxyHandler) {
        AtomicReference<String> authToken;
        final long minTimeout = timeoutMillis;
        final long maxTimeout = timeoutMillis*10;
        try {
            authToken = new AtomicReference<>(generateNewAuthToken());
        }catch(Exception e) {
            System.out.println("Error getting authtoken...");
            e.printStackTrace();
            return;
        }
        for(int i = 0; i < assets.size(); i++) {
            AtomicBoolean retry = new AtomicBoolean(true);
            AtomicInteger tries = new AtomicInteger(0);
            String asset = assets.get(i);
            while (retry.get() && tries.getAndIncrement() < maxRetries) {
                retry.set(false);
                try {
                    String familyData = getFamilyMembersForAssetHelper(asset, authToken.get(), proxyHandler);
                    if (familyData != null) {
                        writer.write(familyData.replace("\n",""));
                        writer.write("\n");
                        writer.flush();
                    }
                } catch(FileNotFoundException fne) {
                    System.out.println("Unable to find: "+asset);
                } catch (Exception e) {
                    timeoutMillis = Math.min(timeoutMillis*2,maxTimeout);
                    e.printStackTrace();
                    retry.set(true);
                    try {
                        System.out.println("Trying to reacquire access token...");
                        String auth = generateNewAuthToken();
                        if(auth!=null) {
                            authToken.set(auth);
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        System.out.println("Unable to reacquire access token.");
                    }
                }
            }
            if(tries.get()>=maxRetries) {
                System.out.println("Max retries reached...");
                break;
            }
            if(tries.get()<=1) {
                timeoutMillis = Math.max(Math.round(0.8*timeoutMillis),minTimeout);
            }
            try {
                System.out.println("Finished: "+(1+i)+" / "+assets.size());
                TimeUnit.MILLISECONDS.sleep(timeoutMillis);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static List<String> getAssetsWithoutFamilyIds(Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select country_code||publication_number from patents_global where family_id='-1' and publication_number is not null and country_code ='US' and not kind_code like 'S%' and not kind_code like 'H%' and not kind_code like 'P%'");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        List<String> assets = new LinkedList<>();
        int i = 0;
        while(rs.next()) {
            // need to convert publication numbers to docdb format
            String asset = rs.getString(1);
            if(asset.startsWith("US")&&asset.length()==13) {
                asset = asset.substring(0,6)+asset.substring(7);
            }
            assets.add(asset);
            if(i%10000==9999) {
                System.out.println("Found "+i+" assets");
            }
            i++;
        }
        rs.close();
        ps.close();
        return assets;
    };

    private static void startProxies(int numProxies) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "gcloud compute instance-groups managed create instance-group1 --base-instance-name test --size "+numProxies+" --template pair-proxy-template-v3 --zone us-west1-a");
        Process process = ps.start();
        process.waitFor();
        TimeUnit.SECONDS.sleep(10);
    }

    private static void stopProxies() throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "gcloud compute instance-groups managed delete instance-group1 --zone us-west1-a");
        Process process = ps.start();
        process.waitFor();
    }

    public static void main(String[] args) throws Exception {
        // START PROXIES
        try {
        //    startProxies(10);
            long timeoutMillisBetweenRequests = 2000;
            Connection conn = Database.getConn();

            List<String> assets = getAssetsWithoutFamilyIds(conn).stream()
                    .filter(asset -> !(asset.contains("D") || asset.contains("RE") || asset.contains("P") || asset.contains("H") || asset.contains("T")))
                    .collect(Collectors.toList());

            ScrapeEPO fullDocumentScraper = new ScrapeEPO();
            ProxyHandler proxyHandler = null;//new ProxyHandler();
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dataDir, "epo" + "_" + LocalDateTime.now().toString())));
            fullDocumentScraper.scrapeFamilyMembersForAssets(assets, 10, writer, timeoutMillisBetweenRequests, proxyHandler);
            writer.close();

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            // CLOSE PROXIES
            try {
       //         stopProxies();
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("Error stopping proxies!!!!!!!");
            }
        }
    }
}
