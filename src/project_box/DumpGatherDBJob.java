package project_box;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;

/**
 * Created by ehallmark on 9/29/17.
 */
public class DumpGatherDBJob implements org.quartz.Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        File file = new File("data/gather_production.dump");
        String executeStatement = "pg_dump -Fc -h data.gttgrp.com -U postgres -d gather_production > "+file.getAbsolutePath();
        try {
            PGDumpLatest.startProcess(executeStatement);
        }catch(Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
