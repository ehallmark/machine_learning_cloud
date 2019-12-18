package seeding.google;

import cpc_normalization.CPCHierarchy;
import mailer.Mailer;
import seeding.Database;
import seeding.ai_db_updater.UpdateBasePatentData;
import seeding.ai_db_updater.UpdateClassificationHash;
import seeding.google.postgres.*;
import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;

import java.sql.Connection;

public class UpdateEPO {
    public static void main(String[] args) {
        // scrape missing family ids
        try {
            //IngestScrapedXMLIntoPostgres.main(args); // get pre
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
