package seeding.google;

import seeding.google.postgres.*;
import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;

public class UpdateAll {
    public static void main(String[] args) {
        try {
            IngestPatentsFromJson.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 1...");
            System.exit(1);
        }

        // add indices to patents_global
        // TODO

        // scrape missing family ids
        try {
            ScrapeEPO.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 2...");
            System.exit(1);
        }

        try {
            IngestScrapedXMLIntoPostgres.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 3...");
            System.exit(1);
        }

        // update assignments
        try {
            IngestAssignmentData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 4...");
            System.exit(1);
        }

        // maintenance data
        try {
            IngestMaintenanceFeeData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 5...");
            System.exit(1);
        }

        // pair data
        try {
            DownloadLatestPAIR.main(args);
            IngestPairData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 6...");
            System.exit(1);
        }

        // ptab data
        try {
            IngestPTABData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 7...");
            System.exit(1);
        }

        // sep
        try {
            IngestSEPFromJson.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 8...");
            System.exit(1);
        }

        // wipo
        try {
            IngestWIPOTechnologies.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 9...");
            System.exit(1);
        }

        // run helper sql commands to build dependent tables
        // TODO

        // more advanced models (tech tag, similarity, etc...)
        // TODO
    }
}
