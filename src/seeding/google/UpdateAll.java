package seeding.google;

import seeding.google.postgres.IngestAssignmentData;
import seeding.google.postgres.IngestPatentsFromJson;
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
    }
}
