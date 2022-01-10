package seeding.google;

import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;

public class UpdateEPO {
    public static void main(String[] args) {
        // scrape missing family ids
        try {
            IngestScrapedXMLIntoPostgres.main(args); // get pre
            System.out.println("Finished loading pre scraped xmls...");
            ScrapeEPO.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage ScrapeEPO...");
            System.exit(1);
        }

        try {
            IngestScrapedXMLIntoPostgres.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestScrapedXMLIntoPostgres...");
            System.exit(1);
        }
    }

}
