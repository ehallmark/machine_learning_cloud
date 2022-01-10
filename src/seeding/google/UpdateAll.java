package seeding.google;

import mailer.Mailer;
import seeding.ai_db_updater.UpdateBasePatentData;
import seeding.ai_db_updater.UpdateAppClassificationHash;
import seeding.ai_db_updater.UpdateGrantClassificationHash;
import seeding.google.postgres.*;

public class UpdateAll {
    public static void main(String[] args) {
        boolean monthUpdate = false;
        final Mailer emailer = new Mailer();
        final String DEFAULT_MAIL_SUBJECT = "PSP Update Error";
        /*
            START OF WEEKLY UPDATES
         */

        // ingest latest us assets
        try {
            UpdateBasePatentData.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage UpdateBasePatentData...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage UpdateBasePatentData...", e);
            System.exit(1);
        }

        // update assignments
        try {
            IngestAssignmentData.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestAssignmentData...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage IngestAssignmentData...", e);
            System.exit(1);
        }


        // maintenance data
        try {
            IngestMaintenanceFeeData.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestMaintenanceFeeData...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage IngestMaintenanceFeeData...", e);
            System.exit(1);
        }

        // pair data
        try {
            if (monthUpdate) {
                DownloadLatestPAIR.main(args);
                IngestPairData.main(args);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestPairData...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage IngestPairData...", e);
            System.exit(1);
        }


        // ptab data
        /* try {
            IngestPTABData.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestPTABData...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage IngestPTABData...", e);
            System.exit(1);
        } */

        // wipo
        try {
            IngestWIPOTechnologies.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestWIPOTechnologies...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage IngestWIPOTechnologies...", e);
            System.exit(1);
        }


        // cpc backup datasource
        try {
            UpdateAppClassificationHash.main(args);
            UpdateGrantClassificationHash.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage UpdateClassificationHash...");
            emailer.sendMail(DEFAULT_MAIL_SUBJECT, "Exited during stage UpdateClassificationHash...", e);
            System.exit(1);
        }
        UpdatePostgresAggregationsInitial.main(args);
        UpdatePostgresAggregationsPost.main(args);
    }

}
