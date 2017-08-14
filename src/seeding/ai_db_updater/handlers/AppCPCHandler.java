package seeding.ai_db_updater.handlers;

import elasticsearch.DataIngester;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class AppCPCHandler implements LineHandler {
    protected Map<String,Set<String>> appToClassificationHash;
    public AppCPCHandler(Map<String,Set<String>> appToClassificationHash) {
        this.appToClassificationHash=appToClassificationHash;
    }

    @Override
    public void save() {
        //Database.saveObject(appToClassificationHash,Database.appToClassificationMapFile);
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
                Map<String,Object> doc = new HashMap<>();
                Set<String> data = appToClassificationHash.get(patNum);
                if(data==null) {
                    data = new HashSet<>();
                    appToClassificationHash.put(patNum, data);
                }
                data.add(ClassCodeHandler.convertToHumanFormat(cpcSubGroup));
                doc.put(Constants.CPC_CODES, data);
                DataIngester.ingestBulk(patNum,doc,false, false);
            }
        }
    }


}
