package mini_server;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by ehallmark on 8/1/17.
 */
public class Server {
    public static final AtomicBoolean keepOn = new AtomicBoolean(true);
    private static final String DEFAULT_URL = "35.184.53.203";
    private static final String DEFAULT_ZONE = "us-central1-a";
    private static final String DEFAULT_INSTANCE_NAME = "instance-2";
    public static void main(String[] args) {
        Timer timer = new Timer();
        LocalDate today = LocalDate.now();
        final long millisecondsPerDay =  24 * 60 * 60 * 1000;
        final long monitorPeriod = 5 * 60 * 1000; // a few minutes
        LocalDateTime startTime = LocalDateTime.of(today.getYear(),today.getMonth(),today.getDayOfMonth(),8,0);
        LocalDateTime endTime = LocalDateTime.of(today.getYear(),today.getMonth(),today.getDayOfMonth(),16,0);
        long startDelay = startTime.atZone(ZoneId.systemDefault()).toEpochSecond()*1000 - System.currentTimeMillis();
        while(startDelay < 0) startDelay += millisecondsPerDay;
        long endDelay = endTime.atZone(ZoneId.systemDefault()).toEpochSecond()*1000 - System.currentTimeMillis();
        while(endDelay < 0) endDelay += millisecondsPerDay;
        System.out.println("Start delay: "+startDelay);
        System.out.println("End delay: "+endDelay);
        { // start up
            StartUpTask startUpTask = new StartUpTask();
            timer.schedule(startUpTask,startDelay,millisecondsPerDay);
        }
        { // shutdown
            ShutDownTask shutDownTask = new ShutDownTask();
            timer.schedule(shutDownTask,endDelay,millisecondsPerDay);
        }
        { // monitor
            MonitorTask monitorTask = new MonitorTask(DEFAULT_URL, DEFAULT_INSTANCE_NAME, DEFAULT_ZONE);
            timer.schedule(monitorTask,0,monitorPeriod);
        }
    }
}
