package project_box;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Created by ehallmark on 9/29/17.
 */
public class PGDumpLatest {
    public static void main(String[] args) throws Exception {
        // Grab the Scheduler instance from the Factory
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        {
            JobDetail compDBJob = newJob(DumpCompDBJob.class)
                    .withIdentity("job1", "group1")
                    .build();
            Trigger compDbTrigger = newTrigger()
                    .withIdentity("trigger1", "group1")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 ? * SAT"))
                    .build();
            scheduler.scheduleJob(compDBJob, compDbTrigger);
        }

        {
            JobDetail gatherDBJob = newJob(DumpGatherDBJob.class)
                    .withIdentity("job2", "group2")
                    .build();
            Trigger gatherDBTrigger = newTrigger()
                    .withIdentity("trigger2", "group2")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 30 1 ? * SAT"))
                    .build();
            scheduler.scheduleJob(gatherDBJob, gatherDBTrigger);
        }

        // and start it off
        scheduler.start();
    }

    public static void startProcess(String statement) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", statement);
        Process process = ps.start();
        process.waitFor();
    }
}
