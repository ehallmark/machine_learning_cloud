package epo;

import com.google.gson.Gson;
import seeding.Database;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Evan on 11/16/2017.
 */
public class ScrapeEPO {

    private static String generateNewAuthToken() throws IOException{
        String key = "AZ42DGb0AeTZ4wwSUWnRoGdGjnP8Gfjc";
        String secret = "O7PG38t4P2uJK2IQ";

        Object auth_token;

        {
            URL url = new URL ("https://ops.epo.org/3.1/auth/accesstoken");
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

        System.out.println("Auth token: "+auth_token);
        return (String)auth_token;
    }

    private static List<Map<String,Object>> getFamilyMembersForAssetHelper(String asset, String auth_token) throws Exception {
        asset="US"+asset;

        if(auth_token!=null) {
            URL url = new URL("http://ops.epo.org/3.1/rest-services/family/publication/epodoc/"+asset);
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
        }

        return null;
    }

    // TODO
    private static List<Map<String,Object>> parseJsonDoc(String json) {
        System.out.println("json: "+json);
        return null;
    }

    public static Map<String,List<Map<String,Object>>> getFamilyMembersForAssets(List<String> assets, int maxRetries) throws Exception {
        Lock refreshLock = new ReentrantLock();
        int batch = 100;

        AtomicReference<String> authToken = new AtomicReference<>(generateNewAuthToken());
        Map<String,List<Map<String,Object>>> dataMap = Collections.synchronizedMap(new ConcurrentHashMap<>(assets.size()));
        for(int i = 0; i < 1+assets.size()/batch; i++) {
            if(i*batch>=assets.size()) continue;
            AtomicBoolean retry = new AtomicBoolean(true);
            AtomicInteger tries = new AtomicInteger(0);
            while (retry.get() && tries.getAndIncrement() < maxRetries) {
                retry.set(false);
                assets.subList(i*batch,Math.min(assets.size(),i*batch+batch)).parallelStream().forEach(asset-> {
                    try {
                        List<Map<String,Object>> familyData = getFamilyMembersForAssetHelper(asset, authToken.get());
                        if(familyData!=null) {
                            dataMap.put(asset,familyData);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        retry.set(true);
                        if (refreshLock.tryLock()) {
                            try {
                                System.out.println("Trying to reacquire access token...");
                                authToken.set(generateNewAuthToken());
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            } finally {
                                refreshLock.unlock();
                            }
                        } else {
                            while(!refreshLock.tryLock()) {
                                try {
                                    TimeUnit.MILLISECONDS.sleep(10);
                                } catch (Exception e2) {
                                    e2.printStackTrace();
                                }
                            }
                            refreshLock.unlock();
                        }
                    }
                });
            }
        }

        return dataMap;
    }

    public static void main(String[] args) throws Exception {

        //test
        getFamilyMembersForAssets(new ArrayList<>(Database.getCopyOfAllPatents()), 4);
    }
}
