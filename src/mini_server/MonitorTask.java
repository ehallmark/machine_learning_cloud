package mini_server;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
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
                System.out.println("Not on.");
                try {
                    turnOn();
                } catch(Exception e) {
                    System.out.println("Error turning on: "+e.getMessage());
                }
            } else {
                System.out.println("Yes.");
            }
        } else {
            // check if already off
            System.out.println("Checking if already off...");
            if (ping(url)) {
                // turn off
                System.out.println("Not off.");
                try {
                    turnOff();
                } catch(Exception e) {
                    System.out.println("Error turning off: "+e.getMessage());
                }
            } else {
                System.out.println("Yes.");
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
            return InetAddress.getByName(url).isReachable(60 * 1000);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Inet Address is invalid: "+url);
        }
    }
}
