package user_interface.server;

import org.apache.commons.io.FileUtils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class RunUpdatesOnly extends PlatformServerManager {
    public static void main(String[] args) {
        System.out.println("Starting updates..");
        performFullUpdateCycle();
    }
}
