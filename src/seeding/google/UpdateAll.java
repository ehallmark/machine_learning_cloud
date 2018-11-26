package seeding.google;

import cpc_normalization.CPCHierarchy;
import seeding.Database;
import seeding.ai_db_updater.UpdateBasePatentData;
import seeding.ai_db_updater.UpdateClassificationHash;
import seeding.google.postgres.*;
import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;

import java.sql.Connection;

public class UpdateAll {
    public static void main(String[] args) {
        boolean updateGoogleJsons = false;
        boolean monthUpdate = false;
        boolean aggsOnly = true;

        if (updateGoogleJsons) {
            /*
                QUARTERLY UPDATES
             */
            try {
                IngestPatentsFromJson.main(args);
                // add indices to patents_global
                Connection conn = Database.getConn();
                conn.createStatement().executeUpdate("create index patents_global_family_id_idx on patents_global (family_id)");
                conn.createStatement().executeUpdate("create index patents_global_publication_num_idx on patents_global (publication_number)");
                conn.createStatement().executeUpdate("create index patents_global_app_num_full_idx on patents_global (application_number_full)");
                conn.createStatement().executeUpdate("create index patents_global_app_and_pub_num_idx on patents_global (publication_number,application_number)");
                conn.commit();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage IngestPatentsFromJson...");
                System.exit(1);
            }

            try {
                IngestCPCDefinitionFromJson.main(args);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage IngestCPCDefinitionFromJson...");
                System.exit(1);
            }

            try {
                CPCHierarchy.updateAndGetLatest();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage CPCHierarchy...");
                System.exit(1);
            }

            // sep
            try {
                IngestSEPFromJson.main(args);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage IngestSEPFromJson...");
                System.exit(1);
            }

        }
        
        /*
            START OF WEEKLY UPDATES
         */

        if(!aggsOnly) {
            // ingest latest us assets
            try {
                UpdateBasePatentData.main(args);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage UpdateBasePatentData...");
                System.exit(1);
            }

            {

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

                // update assignments
                try {
                    IngestAssignmentData.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage IngestAssignmentData...");
                    System.exit(1);
                }


                // maintenance data
                try {
                    IngestMaintenanceFeeData.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage IngestMaintenanceFeeData...");
                    System.exit(1);
                }

                // pair data
                try {
                    //if (monthUpdate) {
                    DownloadLatestPAIR.main(args);
                    //}
                    IngestPairData.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage IngestPairData...");
                    System.exit(1);
                }


                // ptab data
                try {
                    IngestPTABData.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage IngestPTABData...");
                    System.exit(1);
                }

                // wipo
                try {
                    IngestWIPOTechnologies.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage IngestWIPOTechnologies...");
                    System.exit(1);
                }


                // cpc backup datasource
                try {
                    UpdateClassificationHash.main(args);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Exited during stage UpdateClassificationHash...");
                    System.exit(1);
                }
            }
        }
        UpdatePostgresAggregationsInitial.main(args);
        UpdatePostgresAggregationsPost.main(args);
    }

}
