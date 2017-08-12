package seeding.ai_db_updater.handlers;

import elasticsearch.DataIngester;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.*;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class MaintenanceEventHandler implements LineHandler {
    protected AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
    protected ExpiredAssetsMap expiredAssetsMap;
    protected AssetEntityStatusMap entityStatusMap;
    protected AssetToMaintenanceFeeReminderCountMap maintenanceFeeReminderCountMap;
    public MaintenanceEventHandler(ExpiredAssetsMap expiredAssetsMap, AssetEntityStatusMap entityStatusMap, AssetToMaintenanceFeeReminderCountMap maintenanceFeeReminderCountMap) {
        this.expiredAssetsMap=expiredAssetsMap;
        this.entityStatusMap=entityStatusMap;
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
                    data.put(Constants.EXPIRED, true);
                    expiredAssetsMap.getPatentDataMap().get(Constants.EXPIRED).add(patNum);
                    if(appNum!=null) {
                        expiredAssetsMap.getApplicationDataMap().get(Constants.EXPIRED).add(appNum);
                    }
                } else if (maintenanceCode.equals("EXPX")) {
                    // reinstated
                    expiredAssetsMap.getPatentDataMap().get(Constants.EXPIRED).remove(patNum);
                    data.put(Constants.EXPIRED, false);
                    if(appNum!=null) {
                        expiredAssetsMap.getApplicationDataMap().get(Constants.EXPIRED).remove(appNum);
                    }

                } else if (maintenanceCode.equals("REM.")) {
                    // reminder
                    if(maintenanceFeeReminderCountMap.getPatentDataMap().containsKey(patNum)) {
                        maintenanceFeeReminderCountMap.getPatentDataMap().put(patNum,maintenanceFeeReminderCountMap.getPatentDataMap().get(patNum)+1);
                    } else {
                        maintenanceFeeReminderCountMap.getPatentDataMap().put(patNum,1);
                    }
                    if(appNum!=null) {
                        if(maintenanceFeeReminderCountMap.getApplicationDataMap().containsKey(appNum)) {
                            maintenanceFeeReminderCountMap.getApplicationDataMap().put(appNum,maintenanceFeeReminderCountMap.getApplicationDataMap().get(appNum)+1);
                        } else {
                            maintenanceFeeReminderCountMap.getApplicationDataMap().put(appNum,1);
                        }
                    }
                } else if (maintenanceCode.startsWith("M2")||maintenanceCode.startsWith("SM")||maintenanceCode.equals("LTOS")||maintenanceCode.equals("MTOS")) {
                    entityStatusMap.getPatentDataMap().get(Constants.SMALL).add(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.LARGE).remove(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.MICRO).remove(patNum);
                    if(appNum!=null) {
                        entityStatusMap.getApplicationDataMap().get(Constants.SMALL).add(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.LARGE).remove(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.MICRO).remove(appNum);
                    }
                    data.put(Constants.ASSIGNEE_ENTITY_TYPE, Constants.SMALL.toString());
                } else if (maintenanceCode.startsWith("M1")||maintenanceCode.startsWith("LSM")) {
                    entityStatusMap.getPatentDataMap().get(Constants.SMALL).remove(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.LARGE).add(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.MICRO).remove(patNum);
                    if(appNum!=null) {
                        entityStatusMap.getApplicationDataMap().get(Constants.SMALL).remove(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.LARGE).add(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.MICRO).remove(appNum);
                    }
                    data.put(Constants.ASSIGNEE_ENTITY_TYPE, Constants.LARGE.toString());
                } else if(maintenanceCode.startsWith("M3")||maintenanceCode.equals("STOM")) {
                    entityStatusMap.getPatentDataMap().get(Constants.SMALL).remove(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.LARGE).remove(patNum);
                    entityStatusMap.getPatentDataMap().get(Constants.MICRO).add(patNum);
                    if(appNum!=null) {
                        entityStatusMap.getApplicationDataMap().get(Constants.SMALL).remove(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.LARGE).remove(appNum);
                        entityStatusMap.getApplicationDataMap().get(Constants.MICRO).add(appNum);
                    }
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

    public String getUrl() {
        return "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
    }
}
