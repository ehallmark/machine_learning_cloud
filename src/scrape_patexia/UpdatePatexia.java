package scrape_patexia;

import java.time.LocalDate;

public class UpdatePatexia {
    public static void main(String[] args) {



        // step 1: scrape latest data
        try {
            Scraper.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed on "+ LocalDate.now());
            System.out.println("Error during step 1 (rescraping)");
            System.exit(1);
        }
        // step 2: ingest data to postgres
        try {
            ReadScrapedData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed on "+ LocalDate.now());
            System.out.println("Error during step 2 (ingesting)");
            System.exit(1);
        }

        // step 3: set timer to update in 24 hours
        // TODO
    }
}
