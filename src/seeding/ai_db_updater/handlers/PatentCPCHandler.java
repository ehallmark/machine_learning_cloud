package seeding.ai_db_updater.handlers;

import seeding.Database;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class PatentCPCHandler implements LineHandler {
    protected Map<String,Set<String>> patentToClassificationHash = new HashMap<>();

    @Override
    public void save() {
        Database.saveObject(patentToClassificationHash,Database.patentToClassificationMapFile);
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 32) {
            String patNum = line.substring(10, 17).trim();
            String cpcSection = line.substring(17, 18);
            String cpcClass = cpcSection + line.substring(18, 20);
            String cpcSubclass = cpcClass + line.substring(20, 21);
            String cpcMainGroup = cpcSubclass + line.substring(21, 25);
            String cpcSubGroup = cpcMainGroup + line.substring(26, 32);
            if (patentToClassificationHash.containsKey(patNum)) {
                patentToClassificationHash.get(patNum).add(cpcSubGroup);
            } else {
                Set<String> data = new HashSet<>();
                data.add(cpcSubGroup);
                patentToClassificationHash.put(patNum, data);
            }
        }
    }
}
