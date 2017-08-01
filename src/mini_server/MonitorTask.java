package mini_server;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimerTask;

/**
 * Created by ehallmark on 8/1/17.
 */
public class MonitorTask extends TimerTask {
    private String url;
    private String instanceName;
    private String zone;
    public MonitorTask(String url, String instanceName, String zone) {
        this.url = url;
        this.instanceName=instanceName;
        this.zone=zone;
    }

    @Override
    public void run() {
        System.out.println("Monitoring...");
        boolean turnOn = Server.keepOn.get();
        if (turnOn) {
            // check if already on
            System.out.println("Checking if already on...");
            if (!ping(url)) {
                // turn on
                try {
                    turnOn();
                } catch(Exception e) {
                    System.out.println("Error turning on: "+e.getMessage());
                }
            }
        } else {
            // check if already off
            System.out.println("Checking if already off...");
            if (ping(url)) {
                // turn off
                try {
                    turnOff();
                } catch(Exception e) {
                    System.out.println("Error turning off: "+e.getMessage());
                }
            }
        }
    }

    private void turnOn() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec("gcloud compute instances start "+instanceName+" --zone="+zone);
        process.waitFor();
        System.out.println("Started...");
    }

    private void turnOff() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec("gcloud compute instances stop "+instanceName+" --zone="+zone);
        process.waitFor();
        System.out.println("Stopped...");
    }

    private static boolean ping(String url) {
        try {
            HttpURLConnection.setFollowRedirects(false);
            // note : you may also need
            //        HttpURLConnection.setInstanceFollowRedirects(false)
            HttpURLConnection con =
                    (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setInstanceFollowRedirects(false);
            return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
        } catch (Exception e) {
            return false;
        }
    }
}
