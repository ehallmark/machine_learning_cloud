package seeding.google;

import cpc_normalization.CPCHierarchy;
import seeding.Database;
import seeding.ai_db_updater.UpdateBasePatentData;
import seeding.ai_db_updater.UpdateClassificationHash;
import seeding.google.postgres.*;
import seeding.google.postgres.epo.IngestScrapedXMLIntoPostgres;
import seeding.google.postgres.epo.ScrapeEPO;
import seeding.google.tech_tag.FilterKeywordsByTFIDF;
import seeding.google.tech_tag.PredictKeywords;
import seeding.google.tech_tag.PredictTechTags;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.Connection;

public class UpdateAll {
    public static void main(String[] args) {
        boolean updateGoogleJsons = false;
        if(updateGoogleJsons) {
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
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage CPCHierarchy...");
                System.exit(1);
            }

            // sep
            try {
                IngestSEPFromJson.main(args);
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("Exited during stage IngestSEPFromJson...");
                System.exit(1);
            }

        }
        
        /*
            START OF WEEKLY UPDATES
         */

        // ingest latest us assets
        try {
            UpdateBasePatentData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage UpdateBasePatentData...");
            System.exit(1);
        }
        // scrape missing family ids
        try {
            IngestScrapedXMLIntoPostgres.main(args); // get pre
            System.out.println("Finished loading pre scraped xmls...");
            ScrapeEPO.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage ScrapeEPO...");
            System.exit(1);
        }

        try {
            IngestScrapedXMLIntoPostgres.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestScrapedXMLIntoPostgres...");
            System.exit(1);
        }

        // update assignments
        try {
            IngestAssignmentData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestAssignmentData...");
            System.exit(1);
        }

        // maintenance data
        try {
            IngestMaintenanceFeeData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestMaintenanceFeeData...");
            System.exit(1);
        }

        // pair data
        try {
            DownloadLatestPAIR.main(args);
            IngestPairData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestPairData...");
            System.exit(1);
        }

        // ptab data
        try {
            IngestPTABData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestPTABData...");
            System.exit(1);
        }

        // wipo
        try {
            IngestWIPOTechnologies.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage IngestWIPOTechnologies...");
            System.exit(1);
        }

        // cpc backup datasource
        try {
            UpdateClassificationHash.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exited during stage UpdateClassificationHash...");
            System.exit(1);
        }


        // AGGREGATIONS (do family id idx first)

        // run helper sql commands to build dependent tables
        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/family_id_idx.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute family_id_idx...");
            System.exit(1);
        }


        // cpc trees
        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/cpc_tree.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute cpc_tree...");
            System.exit(1);
        }


        // citations_and_pclaims
        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/citations_and_pclaims.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute citations_and_pclaims...");
            System.exit(1);
        }


        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/maintenance_codes_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute maintenance_codes_aggs...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/pair_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute pair_aggs...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/wipo_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute wipo_aggs...");
            System.exit(1);
        }


        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/ptab_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute ptab_aggs...");
            System.exit(1);
        }



        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/reissue.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute reissue...");
            System.exit(1);
        }


        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/patent_text_aggs.sql"));
            PredictTechTags.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute patent_text_aggs...");
            System.exit(1);
        }

        try {
            PredictKeywords.main(args);
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/patent_keyword_aggs.sql"));
            FilterKeywordsByTFIDF.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute patent_keyword_aggs...");
            System.exit(1);
        }


        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/assignment_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute assignment_aggs...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/assignee_table.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute assignee_table...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/latest_assignees.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute latest_assignees...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/granted.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute granted...");
            System.exit(1);
        }

        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/ai_value.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute ai_value...");
            System.exit(1);
        }


        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/tech_tags_aggs.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute tech_tags_aggs...");
            System.exit(1);
        }


        // SIMILARITY
        try {
            // run python code under gtt_models/src/java_compatibility/BuildPatentEncodings.py
            // cd ~/repos/gtt_models/
            // git pull origin master
            // . ~/environments/tfenv/bin/activate
            // python3 BuildPatentEncodings.py
            // cd ~/repos/machine_learning_cloud/
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/embedding_aggs.sql"));
            IngestAssigneeEmbeddingsToPostgres.main(args);

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute BuildPatentEncodings...");
            System.exit(1);
        }


        Database.close();

        // MERGE RESULTS
        try {
            runSqlTable(new File("src/seeding/google/postgres/attribute_tables/merge_patents_for_es.sql"));
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute merge_patents_for_es...");
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
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "psql patentdb < "+file.getAbsolutePath());
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
