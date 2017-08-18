package seeding.ai_db_updater.handlers;

import elasticsearch.DataIngester;
import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.LapsedAttribute;
import user_interface.ui_models.attributes.hidden_attributes.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/12/17.
 */
public class MaintenanceEventHandler implements LineHandler {
    protected AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
    protected LapsedAttribute lapsedAssetMap;
    protected AssetToMaintenanceFeeReminderCountMap maintenanceFeeReminderCountMap;
    public MaintenanceEventHandler(LapsedAttribute lapsedAssetMap, AssetToMaintenanceFeeReminderCountMap maintenanceFeeReminderCountMap) {
        this.lapsedAssetMap=lapsedAssetMap;
        this.maintenanceFeeReminderCountMap = maintenanceFeeReminderCountMap;
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 50) {
            String patNum = line.substring(0, 7);
            String maintenanceCode = line.substring(46, 51).trim();
            if (patNum != null && maintenanceCode != null ) {
                String filing = assetToFilingMap.getPatentDataMap().get(patNum);
                String appNum = null;
                if (filing != null) {
                    appNum = filingToAssetMap.getApplicationDataMap().get(filing);
                }

                Map<String,Object> data = new HashMap<>();
                if(maintenanceCode.equals("EXP.")) {
                    data.put(Constants.LAPSED, true);
                    lapsedAssetMap.getPatentDataMap().get(Constants.LAPSED).add(patNum);
                    if(appNum!=null) {
                        lapsedAssetMap.getApplicationDataMap().get(Constants.LAPSED).add(appNum);
                    }
                } else if (maintenanceCode.equals("EXPX")) {
                    // reinstated
                    lapsedAssetMap.getPatentDataMap().get(Constants.LAPSED).remove(patNum);
                    data.put(Constants.REINSTATED, true);
                    data.put(Constants.LAPSED, false);
                    if(appNum!=null) {
                        lapsedAssetMap.getApplicationDataMap().get(Constants.LAPSED).remove(appNum);
                    }

                } else if (maintenanceCode.equals("REM.")) {
                    // reminder
                    if(maintenanceFeeReminderCountMap.getPatentDataMap().containsKey(patNum)) {
                        maintenanceFeeReminderCountMap.getPatentDataMap().put(patNum,maintenanceFeeReminderCountMap.getPatentDataMap().get(patNum)+1);
                    } else {
                        maintenanceFeeReminderCountMap.getPatentDataMap().put(patNum,1);
                    }
                    int maintenanceFeeCount = maintenanceFeeReminderCountMap.getPatentDataMap().get(patNum);
                    data.put(Constants.MAINTENANCE_FEE_REMINDERS, maintenanceFeeCount);
                    if(appNum!=null) {
                        maintenanceFeeReminderCountMap.getApplicationDataMap().put(appNum,maintenanceFeeCount);
                    }
                } else if (maintenanceCode.startsWith("M2")||maintenanceCode.startsWith("SM")||maintenanceCode.equals("LTOS")||maintenanceCode.equals("MTOS")) {
                    data.put(Constants.ASSIGNEE_ENTITY_TYPE, Constants.SMALL.toString());
                } else if (maintenanceCode.startsWith("M1")||maintenanceCode.startsWith("LSM")) {
                    data.put(Constants.ASSIGNEE_ENTITY_TYPE, Constants.LARGE.toString());
                } else if(maintenanceCode.startsWith("M3")||maintenanceCode.equals("STOM")) {
                    data.put(Constants.ASSIGNEE_ENTITY_TYPE, Constants.MICRO.toString());
                }

                if(data.size()>0) {
                    DataIngester.ingestBulk(patNum, data, false);
                    if (appNum != null) {
                        DataIngester.ingestBulk(appNum, data, false);
                    }
                }

            }
        }
    }

    @Override
    public void save() {

    }

}
