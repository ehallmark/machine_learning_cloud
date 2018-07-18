package seeding.ai_db_updater.handlers;

import tools.ClassCodeHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class AppCPCHandler implements LineHandler {
    protected Map<String,Set<String>> appToClassificationHash;
    protected Set<String> valid;
    public AppCPCHandler(Map<String,Set<String>> appToClassificationHash, Set<String> valid) {
        this.appToClassificationHash=appToClassificationHash;
        this.valid = valid;
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
            if(valid.contains(full_publication_number)) {
                String cpcSection = line.substring(21, 22);
                String cpcClass = cpcSection + line.substring(22, 24);
                String cpcSubclass = cpcClass + line.substring(24, 25);
                String cpcMainGroup = cpcSubclass + line.substring(25, 29);
                String cpcSubGroup = cpcMainGroup + line.substring(30, 36);
                Set<String> data = appToClassificationHash.get(full_publication_number);
                if (data == null) {
                    data = new HashSet<>();
                    appToClassificationHash.put(full_publication_number, data);
                }
                data.add(ClassCodeHandler.convertToHumanFormat(cpcSubGroup));
            }
        }
    }


}
