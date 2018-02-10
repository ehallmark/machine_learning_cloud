package epo;

import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MergeEPOMaps {
    public static final File mainFile = new File(Constants.DATA_FOLDER+"all_merged_epo_asset_to_family_data_maps.jobj");
    public static void main(String[] args) {
        File dir = ScrapeEPO.mapDir;

        File[] files = dir.listFiles();

        if(files.length>0) {
            Map<String, List<Map<String, Object>>> finalMap = Collections.synchronizedMap(new HashMap<>());
            for (File mapFile : files) {
                Map<String, List<Map<String, Object>>> currMap = (Map<String, List<Map<String, Object>>>) Database.tryLoadObject(mapFile);
                if (currMap != null) {
                    finalMap.putAll(currMap);
                }
            }
            Database.trySaveObject(finalMap, mainFile);
        }
    }
    static Map<String,List<Map<String,Object>>> loadMergedMap(boolean remerge) {
        if(remerge) main(null);
        return (Map<String,List<Map<String,Object>>>) Database.tryLoadObject(mainFile);
    }

    public static Map<String,List<Map<String,Object>>> loadMergedMap() {
        return loadMergedMap(false);
    }
}
