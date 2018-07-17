package seeding.google.postgres.epo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.stream.Stream;

public class ProxyHandler {
    public static final String PROXY_PREFIX = "http://";
    public static final String PROXY_SUFFIX = ":8080/epo?asset={ASSET}&auth_token={AUTH_TOKEN}";
    public static final Random random = new Random(2351251);
    public static String[] IP_ADDRESSES;
    static {
        try {
            loadIPAddresses();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    private static synchronized void loadIPAddresses() throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "gcloud --format=\"value(networkInterfaces[0].accessConfigs[0].natIP)\" compute instances list");
        Process process = ps.start();
        process.waitFor();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ( (line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.lineSeparator());
        }
        String result = builder.toString();
        String[] ips = result.split("\\s+");
        IP_ADDRESSES = Stream.of(ips).filter(ip->!ip.trim().isEmpty()).map(ip->ip.trim()).toArray(s->new String[s]);
        System.out.println("Found ips: "+String.join("; ",IP_ADDRESSES));
        if(IP_ADDRESSES.length==0) {
            IP_ADDRESSES=null;
        }
    }

    private static String nextRandomIP() {
        if(IP_ADDRESSES==null||IP_ADDRESSES.length==0) {
            synchronized (ProxyHandler.class) {
                if(IP_ADDRESSES==null||IP_ADDRESSES.length==0) {
                    try {
                        loadIPAddresses();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Unable to get next random ip address...");
                    }
                }
            }
        }
        if(IP_ADDRESSES.length==0) {
            throw new RuntimeException("No ip addresses available...");
        }
        return IP_ADDRESSES[random.nextInt(IP_ADDRESSES.length)];
    }

    public HttpURLConnection getProxyUrlForApplication(String asset, String auth_token) {
        return getProxyUrlForApplication(asset, auth_token, 2);
    }


    public HttpURLConnection getProxyUrlForApplication(String asset, String auth_token, int maxRetries) {
        String ip = nextRandomIP();
        try {
            String urlStr = PROXY_PREFIX + ip + PROXY_SUFFIX;
            urlStr = urlStr.replace("{ASSET}", asset);
            urlStr = urlStr.replace("{AUTH_TOKEN}", auth_token);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            System.out.println("URL: "+url);
            if(conn.getResponseCode()==200) {
                return conn;
            } else {
                System.out.println("Error code from connection: "+conn.getResponseCode());
            }
        } catch(MalformedURLException mue) {
            throw new RuntimeException(mue);
        } catch(IOException ioe) {
            System.out.println("Error reading stream...");
        }
        if(maxRetries>0) {
            System.out.println("Retrying...");
            return getProxyUrlForApplication(asset, auth_token,maxRetries-1);
        } else if(maxRetries==0) {
            // last chance
            synchronized (ProxyHandler.class) {
                try {
                    System.out.println("Trying to reload ip addresses...");
                    loadIPAddresses();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return getProxyUrlForApplication(asset, auth_token,-1);
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // TESTING
        ProxyHandler handler = new ProxyHandler();
        loadIPAddresses();
        //System.out.println("Should be 200: "+handler.getProxyUrlForApplication("12900011").getResponseCode());
    }
}
