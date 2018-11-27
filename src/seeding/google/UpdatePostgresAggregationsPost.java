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

public class UpdatePostgresAggregationsPost {
    public static void main(String[] args) {
        final Mailer emailer = new Mailer();
        final String DEFAULT_MAIL_SUBJECT = "PSP Post Aggregations Error";
        try {
            System.out.println("Predicting keywords...");
            PredictKeywords.main(args);
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/patent_keywords_aggs.sql"));
            FilterKeywordsByTFIDF.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute patent_keyword_aggs...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute patent_keyword_aggs...", e);
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/assignment_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute assignment_aggs...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute assignment_aggs...", e);
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/latest_assignee.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute latest_assignees...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute latest_assignees...", e);
            System.exit(1);
        }

        // assignee guesses
        try {
            AssigneeGuess.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error during assignee guessing...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute assignee guessing...", e);
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/granted.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute granted...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute granted...", e);
            System.exit(1);
        }

        try {
            System.out.println("Predicting AI values...");
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/ai_value.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute ai_value...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute ai_value...", e);
            System.exit(1);
        }

        // SIMILARITY
        try {
            System.out.println("Predicting similarity vectors...");
            runProcess(". "+new File("scripts/production/update_similarity.sh"));
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/embedding_aggs.sql"));
            //if(monthUpdate) {
            //    runProcess(". "+new File("scripts/production/update_cpc_similarity.sh"));
            //    IngestAssigneeEmbeddingsToPostgres.main(args);
            //}

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute BuildPatentEncodings...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute BuildPatentEncodings...", e);
            System.exit(1);
        }

        // wipo prediction
        try {
            System.out.println("Predicting wipo technologies for missing vectors...");
            runProcess(". "+new File("scripts/production/update_wipo.sh"));
            //if(monthUpdate) {
            //    runProcess(". "+new File("scripts/production/update_cpc_similarity.sh"));
            //    IngestAssigneeEmbeddingsToPostgres.main(args);
            //}

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute Predicting wipo technologies...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute Predicting wipo technologies...", e);
            System.exit(1);
        }

        // must run tech tagger after similarity model
        try {
            System.out.println("Predicting tech tags...");
            PredictTechTags.main(args);
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/tech_tags_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute tech_tags_aggs...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute Predicting tech tags...", e);
            System.exit(1);
        }

        Database.close();

        // MERGE RESULTS
        try {
            System.out.println("Merging final results...");
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/merge_patents_for_es.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute merge_patents_for_es...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Failed to execute Merging final results...", e);
            System.exit(1);
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
