package seeding.google;

import mailer.Mailer;
import models.assignee.entity_prediction.AssigneeGuess;
import seeding.Database;
import seeding.google.tech_tag.FilterKeywordsByTFIDF;
import seeding.google.tech_tag.PredictKeywords;
import seeding.google.tech_tag.PredictTechTags;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UpdatePostgresAggregationsInitial {
    public static void main(String[] args) {
        // run helper sql commands to build dependent tables
        final Mailer emailer = new Mailer();
        final String DEFAULT_MAIL_SUBJECT = "PSP Pre Aggregations Error";
        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/family_id_idx.sql"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute family_id_idx...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute family_id_idx...", e);
            System.exit(1);
        }

        {
            ExecutorService service = Executors.newFixedThreadPool(4);
            service.execute(() -> {
                // cpc trees
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/cpc_tree.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute cpc_tree...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute cpc_tree...", e);
                    System.exit(1);
                }
            });

            service.execute(() -> {
                // citations_and_pclaims
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/citations_and_pclaims.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute citations_and_pclaims...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute citations_and_pclaims...", e);
                    System.exit(1);
                }

                try {
                    // NOTE: Must be run after priority claim aggregations...
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/reissue.sql"));

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute reissue...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute reissue...", e);
                    System.exit(1);
                }
            });

            service.execute(() -> {
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/maintenance_codes_aggs.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute maintenance_codes_aggs...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute maintenance_codes_aggs...", e);
                    System.exit(1);
                }
            });


            service.execute(() -> {
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/pair_aggs.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute pair_aggs...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute pair_aggs...", e);
                    System.exit(1);
                }
            });


            service.execute(() -> {
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/wipo_aggs.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute wipo_aggs...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute wipo_aggs...", e);
                    System.exit(1);
                }
            });

            service.execute(() -> {
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/ptab_aggs.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute ptab_aggs...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute ptab_aggs...", e);
                    System.exit(1);
                }
            });


            service.execute(() -> {
                try {
                    runSqlTable(new File("src/seeding/google/postgres/attribute_tables/patent_text_aggs.sql"));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to execute patent_text_aggs...");
                    emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute patent_text_aggs...", e);
                    System.exit(1);
                }
            });

            service.shutdown();
            try {
                service.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

    }

    private static BufferedReader getOutput(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream()));
    }

    private static BufferedReader getError(Process p) {
        return new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }


    private static void runSqlTable(File file) throws Exception {
        System.out.println("Starting sql file: "+file.getName());
        runProcess("psql patentdb < "+file.getAbsolutePath());
    }

    private static void runProcess(String proc) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", proc);
        Process process = ps.start();
        BufferedReader output = getOutput(process);
        BufferedReader error = getError(process);
        String line;
        while ((line = output.readLine()) != null) {
            System.out.println(line);
        }
        while ((line = error.readLine()) != null) {
            System.out.println(line);
        }
        process.waitFor();
    }
}
