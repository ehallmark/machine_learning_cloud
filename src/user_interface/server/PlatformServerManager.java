package user_interface.server;

import org.apache.commons.io.FileUtils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class PlatformServerManager implements Job {
    private static final File pidFile = new File("/home/ehallmark/repos/machine_learning_cloud/app.pid");

    private static void runBashProcess(String statement) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", statement);
        ps.redirectErrorStream(true);
        Process process = ps.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }
        System.out.println("Ok.");
        process.waitFor();
    }

    private static void runScriptProcess(File scriptFile) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/sh", scriptFile.getAbsolutePath());
        ps.redirectErrorStream(true);
        Process process = ps.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }
        System.out.println("Ok.");
        process.waitFor();
    }

    private static void stopServer() throws Exception {
        if(pidFile.exists()) {
            System.out.println("Stopping server...");
            String pid = FileUtils.readFileToString(pidFile);
            if(pid!=null&&pid.trim().length()>0) {
                pid=pid.trim();
                int p = Integer.valueOf(pid);
                runBashProcess("kill "+p);
                runBashProcess("while ps -p "+p+"; do sleep 1; done;");
            }
            pidFile.delete();
        }
    }

    private static void backupServer() throws Exception {
        System.out.println("Backing up server...");
        final File scriptFile = new File("/home/ehallmark/repos/machine_learning_cloud/scripts/production/backup.sh");
        runScriptProcess(scriptFile);
    }

    private static void startServer() throws Exception {
        System.out.println("Starting server...");
        if(pidFile.exists()) {
            System.out.println("Previous server instance exists...");
            stopServer();
        }
        final File scriptFile = new File("/home/ehallmark/repos/machine_learning_cloud/scripts/production/start.sh");
        runScriptProcess(scriptFile);
    }

    private static void updateServer() throws Exception {
        final File scriptFile = new File("/home/ehallmark/repos/machine_learning_cloud/scripts/production/update.sh");
        runScriptProcess(scriptFile);
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        performFullUpdateCycle();
    }

    protected static void performFullUpdateCycle() {
        // stop server
        try{
            stopServer();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while stopping server...");
        }

        // setup weekly updates
        try {
            updateServer();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while updating server...");
        }

        // restart server
        try {
            startServer();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while restarting server...");
        }

        // backup server
        try {
            backupServer();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while backing up server...");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting server initially...");
        startServer();
        System.out.println("Starting to schedule jobs...");

        // Grab the Scheduler instance from the Factory
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        {
            JobDetail runPlatformJob = newJob(PlatformServerManager.class)
                    .withIdentity("job1", "group1")
                    .build();
            Trigger weeklyTrigger = newTrigger()
                    .withIdentity("trigger1", "group1")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 30 3 ? * SAT"))
                    .build();
            scheduler.scheduleJob(runPlatformJob, weeklyTrigger);
        }

        // and start it off
       // TODO automatically start with cmd: scheduler.start();
    }
}
