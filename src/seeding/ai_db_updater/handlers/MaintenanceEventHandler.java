package seeding.ai_db_updater.handlers;

import seeding.Database;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class MaintenanceEventHandler implements LineHandler {
    protected Map<String,Integer> patentToMaintenanceFeeReminderCount = new HashMap<>();
    protected Set<String> expiredPatentSet = new HashSet<>();
    protected Set<String> largeEntityPatents = new HashSet<>();
    protected Set<String> smallEntityPatents = new HashSet<>();
    protected Set<String> microEntityPatents = new HashSet<>();

    @Override
    public void handleLine(String line) {
        if (line.length() >= 50) {
            String patNum = line.substring(0, 7);
            String maintenanceCode = line.substring(46, 51).trim();
            if (patNum != null && maintenanceCode != null ) {
                if(maintenanceCode.equals("EXP.")) {
                    expiredPatentSet.add(patNum);
                } else if (maintenanceCode.equals("EXPX")) {
                    // reinstated
                    if(expiredPatentSet.contains(patNum)) {
                        expiredPatentSet.remove(patNum);
                    }
                } else if (maintenanceCode.equals("REM.")) {
                    // reminder
                    if(patentToMaintenanceFeeReminderCount.containsKey(patNum)) {
                        patentToMaintenanceFeeReminderCount.put(patNum,patentToMaintenanceFeeReminderCount.get(patNum)+1);
                    } else {
                        patentToMaintenanceFeeReminderCount.put(patNum,1);
                    }
                } else if (maintenanceCode.startsWith("M2")||maintenanceCode.startsWith("SM")||maintenanceCode.equals("LTOS")||maintenanceCode.equals("MTOS")) {
                    smallEntityPatents.add(patNum);
                    if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                    if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                } else if (maintenanceCode.startsWith("M1")||maintenanceCode.startsWith("LSM")) {
                    largeEntityPatents.add(patNum);
                    if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                    if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                } else if(maintenanceCode.startsWith("M3")||maintenanceCode.equals("STOM")) {
                    microEntityPatents.add(patNum);
                    if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                    if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                }
            }
        }
    }

    @Override
    public void save() {
        Database.saveObject(expiredPatentSet,Database.expiredPatentSetFile);

        Database.saveObject(largeEntityPatents,new File("large_entity_patents_set.jobj"));

        File smallEntityPatentFile = new File("small_entity_patents_set.jobj");
        Database.saveObject(smallEntityPatents,smallEntityPatentFile);

        File microEntityPatentFile = new File("micro_entity_patents_set.jobj");
        Database.saveObject(microEntityPatents,microEntityPatentFile);

        File patentToFeeReminderMapFile = new File("patent_to_fee_reminder_count_map.jobj");
        Database.saveObject(patentToMaintenanceFeeReminderCount,patentToFeeReminderMapFile);

    }

    public String getUrl() {
        return "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
    }
}
