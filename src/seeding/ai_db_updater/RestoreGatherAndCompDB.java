package seeding.ai_db_updater;

import project_box.PGDumpLatest;

import java.io.File;

/**
 * Created by Evan on 9/30/2017.
 */
public class RestoreGatherAndCompDB {
    public static void main(String[] args) {
        try {
            File compdbFile = new File("data/compdb_production.dump");
            if(!compdbFile.exists()) throw new RuntimeException("compdb dump file does not exist...");
            String compDBRestore = "pg_restore -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/compdb_production "+compdbFile.getAbsolutePath();
            String dropCompDB = "dropdb compdb_production";
            PGDumpLatest.startProcess(dropCompDB);
            System.out.println("Dropped compdb successfully...");
            PGDumpLatest.startProcess(compDBRestore);
        } catch(Exception e) {
            System.out.println("Error restoring compdb...");
            e.printStackTrace();
            System.exit(1);
        }

        try {
            File gatherFile = new File("data/gather_production.dump");
            if(!gatherFile.exists()) throw new RuntimeException("gather dump file does not exist...");
            String gatherDBRestore = "pg_restore -Fc --dbname=postgresql://postgres:password@127.0.0.1:5432/gather_production "+gatherFile.getAbsolutePath();
            String dropGather = "dropdb gather_production";
            PGDumpLatest.startProcess(dropGather);
            System.out.println("Dropped gather successfully...");
            PGDumpLatest.startProcess(gatherDBRestore);
        } catch(Exception e) {
            System.out.println("Error restoring gather...");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
