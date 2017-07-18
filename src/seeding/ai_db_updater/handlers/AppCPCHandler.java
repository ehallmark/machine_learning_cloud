package seeding.ai_db_updater.handlers;

import seeding.Database;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class AppCPCHandler implements LineHandler {
    protected Map<String,Set<String>> appToClassificationHash = new HashMap<>();

    @Override
    public void save() {
        Database.saveObject(appToClassificationHash,Database.appToClassificationMapFile);
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 35) {
            String patNum = line.substring(10, 21).trim();
            if(patNum.startsWith("20")) {
                String cpcSection = line.substring(21, 22);
                String cpcClass = cpcSection + line.substring(22, 24);
                String cpcSubclass = cpcClass + line.substring(24, 25);
                String cpcMainGroup = cpcSubclass + line.substring(25, 29);
                String cpcSubGroup = cpcMainGroup + line.substring(30, 36);
                if (appToClassificationHash.containsKey(patNum)) {
                    appToClassificationHash.get(patNum).add(cpcSubGroup);
                } else {
                    Set<String> data = new HashSet<>();
                    data.add(cpcSubGroup);
                    appToClassificationHash.put(patNum, data);
                }
            }
        }
    }


}
