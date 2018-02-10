package epo;

import com.google.gson.Gson;
import seeding.Database;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

        if(auth_token==null) {
            throw new NullPointerException("Auth_token is null.");
        }
        System.out.println("Auth token: "+auth_token);
        return (String)auth_token;
    }

    private AtomicInteger cnt = new AtomicInteger(0);
    private String getFamilyMembersForAssetHelper(String asset, String auth_token) throws Exception {
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

    protected String parseJsonDoc(String json) {
        if(cnt.getAndIncrement()%100==99) {
            System.out.print("-");
        }
        if(cnt.get()%1000==0) {
            System.out.println(" Completed: "+cnt.get());
        }
        return json;
    }

    public void scrapeFamilyMembersForAssets(List<String> assets, int maxRetries, BufferedWriter writer, int computerNum) {
        Set<String> assetsSeenSoFar = Collections.synchronizedSet(new HashSet<>());

        AtomicReference<String> authToken;
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
                    String familyData = getFamilyMembersForAssetHelper(asset, authToken.get());
                    assetsSeenSoFar.add(asset);
                    if(familyData!=null) {
                        writer.write(familyData);
                        writer.flush();
                    }
                } catch (Exception e) {
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
        }
        saveCurrentResults(assetsSeenSoFar,computerNum);
    }

    private void saveCurrentResults(Set<String> seenSoFar, int computerNum) {
        Database.trySaveObject(seenSoFar, new File(assetsSeenFile.getAbsolutePath()+computerNum));
    }

    public static void main(String[] args) throws IOException{
        if(args.length==0) throw new RuntimeException("Please enter the computer number as argument 1.");
        // The computer number determines which assets the computer will look at
        //  to allow for easier concurrency among computers

        int computerNumber = Integer.valueOf(args[0]);
        int limitPerComputer = 100000;

        Set<String> seenSoFar = (Set<String>) Database.tryLoadObject(assetsSeenFile);

        List<String> assets = Database.getCopyOfAllPatents()
                .parallelStream().filter(p->seenSoFar==null||!seenSoFar.contains(p))
                .filter(p->p.endsWith(String.valueOf(computerNumber)))
                .limit(limitPerComputer)
                .collect(Collectors.toList());

        //test
        ScrapeEPO fullDocumentScraper = new ScrapeEPO();

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dataDir, "epo"+computerNumber+"_"+ LocalDate.now().toString())));
        fullDocumentScraper.scrapeFamilyMembersForAssets(assets, 10, writer, computerNumber);
        writer.close();
    }
}
