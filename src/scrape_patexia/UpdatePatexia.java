package scrape_patexia;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import user_interface.server.PlatformServerManager;

import java.time.LocalDate;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class UpdatePatexia implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        // step 1: scrape latest data
        try {
            Scraper.main(null);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed on "+ LocalDate.now());
            System.out.println("Error during step 1 (rescraping)");
            System.exit(1);
        }
        // step 2: ingest data to postgres
        try {
            ReadScrapedData.main(null);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed on "+ LocalDate.now());
            System.out.println("Error during step 2 (ingesting)");
            System.exit(1);
        }
    }


    public static void main(String[] args) throws SchedulerException {
        // Grab the Scheduler instance from the Factory
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        {
            JobDetail runPlatformJob = newJob(PlatformServerManager.class)
                    .withIdentity("update_patexia", "scraper")
                    .build();
            Trigger weeklyTrigger = newTrigger()
                    .withIdentity("trigger_patexia", "scraper")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 ? * FRI"))
                    .build();
            scheduler.scheduleJob(runPlatformJob, weeklyTrigger);
        }

        // and start it off
        scheduler.start();
    }


}
