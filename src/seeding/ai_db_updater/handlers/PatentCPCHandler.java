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
public class PatentCPCHandler implements LineHandler {
    protected Connection conn;
    public PatentCPCHandler(Connection conn) {
        this.conn=conn;
    }

    @Override
    public void save() {
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 33) {
            String kind_code = line.substring(0,2).trim();
            String patNum = line.substring(10, 18).trim();
            String full_publication_number = "US"+patNum+kind_code;
            String cpcSection = line.substring(18, 19);
            String cpcClass = cpcSection + line.substring(19, 21);
            String cpcSubclass = cpcClass + line.substring(21, 22);
            String cpcMainGroup = cpcSubclass + line.substring(22, 26);
            String cpcSubGroup = cpcMainGroup + line.substring(27, 33);
            UpdateClassificationHash.ingestResult(full_publication_number,  ClassCodeHandler.convertToHumanFormat(cpcSubGroup), conn);
        }
    }
}
