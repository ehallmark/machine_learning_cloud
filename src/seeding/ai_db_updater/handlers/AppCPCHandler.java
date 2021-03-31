package seeding.ai_db_updater.handlers;

import seeding.ai_db_updater.UpdateClassificationHash;
import tools.ClassCodeHandler;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class AppCPCHandler implements LineHandler {
    protected Connection conn;
    public AppCPCHandler(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void save() {
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 35) {
            String kind_code = line.substring(0,2).trim();
            String patNum = line.substring(10, 21).trim();
            String full_publication_number = "US"+patNum+kind_code;
            String cpcSection = line.substring(21, 22);
            String cpcClass = cpcSection + line.substring(22, 24);
            String cpcSubclass = cpcClass + line.substring(24, 25);
            String cpcMainGroup = cpcSubclass + line.substring(25, 29);
            String cpcSubGroup = cpcMainGroup + line.substring(30, 36);
            UpdateClassificationHash.ingestResult(full_publication_number,  ClassCodeHandler.convertToHumanFormat(cpcSubGroup), conn);
        }
    }


}
