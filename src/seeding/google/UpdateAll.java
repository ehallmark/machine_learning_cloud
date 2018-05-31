package seeding.google;

import seeding.Database;
import seeding.google.postgres.*;
import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;

import java.sql.Connection;

public class UpdateAll {
    public static void main(String[] args) {
        try {
            IngestPatentsFromJson.main(args);
            // add indices to patents_global
            Connection conn = Database.getConn();
            conn.createStatement().executeUpdate("create index patents_global_family_id_idx on patents_global (family_id)");
            conn.createStatement().executeUpdate("create index patents_global_publication_num_idx on patents_global (publication_number)");
            conn.createStatement().executeUpdate("create index patents_global_app_num_full_idx on patents_global (application_number_full)");
            conn.createStatement().executeUpdate("create index patents_global_app_and_pub_num_idx on patents_global (publication_number,application_number)");
            conn.commit();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage 1...");
            System.exit(1);
        }

        // scrape missing family ids
        try {
            IngestScrapedXMLIntoPostgres.main(args); // get pre
            System.out.println("Finished loading pre scraped xmls...");
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
        // TODO\

        Database.close();
    }
}
