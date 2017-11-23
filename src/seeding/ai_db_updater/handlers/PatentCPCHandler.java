package seeding.ai_db_updater.handlers;

import elasticsearch.DataIngester;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class PatentCPCHandler implements LineHandler {
    protected static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected Map<String,Set<String>> patentToClassificationHash;
    public PatentCPCHandler(Map<String,Set<String>> patentToClassificationHash) {
        this.patentToClassificationHash=patentToClassificationHash;
    }

    @Override
    public void save() {
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 32) {
            String patNum = line.substring(10, 17).trim();
            if (assetToFilingMap.getPatentDataMap().containsKey(patNum)) {
                String cpcSection = line.substring(17, 18);
                String cpcClass = cpcSection + line.substring(18, 20);
                String cpcSubclass = cpcClass + line.substring(20, 21);
                String cpcMainGroup = cpcSubclass + line.substring(21, 25);
                String cpcSubGroup = cpcMainGroup + line.substring(26, 32);
                Set<String> data = patentToClassificationHash.get(patNum);
                if(data==null) {
                    data = new HashSet<>();
                    patentToClassificationHash.put(patNum, data);
                }
                data.add(ClassCodeHandler.convertToHumanFormat(cpcSubGroup));
            }
        }
    }
}
