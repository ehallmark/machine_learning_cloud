package project_box;

import java.io.IOException;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;

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
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 30 9 * * SAT"))
                    .build();
            scheduler.scheduleJob(compDBJob, compDbTrigger);
        }

        {
            JobDetail gatherDBJob = newJob(DumpGatherDBJob.class)
                    .withIdentity("job2", "group2")
                    .build();
            Trigger gatherDBTrigger = newTrigger()
                    .withIdentity("trigger2", "group2")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 10 * * SAT"))
                    .build();
            scheduler.scheduleJob(gatherDBJob, gatherDBTrigger);
        }

        // and start it off
        scheduler.start();
    }

    static void startProcess(String statement) throws IOException {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", statement);
        ps.start();
    }
}
