package seeding.ai_db_updater;

import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.AppCPCHandler;
import seeding.ai_db_updater.handlers.PatentCPCHandler;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * Created by ehallmark on 1/20/17.
 */
public class UpdateClassificationHash {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        pool.execute(new RecursiveAction() {
            @Override
            protected void compute() {
                Database.setupClassificationsHash(new File("patent-cpc-zip/"), new File("patent-cpc-dest/"), Constants.PATENT_CPC_URL_CREATOR, new PatentCPCHandler());
            }
        });
        pool.execute(new RecursiveAction() {
            @Override
            protected void compute() {
                Database.setupClassificationsHash(new File("app-cpc-zip/"), new File("app-cpc-dest/"), Constants.APP_CPC_URL_CREATOR, new AppCPCHandler());
            }
        });

        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
    }
}
