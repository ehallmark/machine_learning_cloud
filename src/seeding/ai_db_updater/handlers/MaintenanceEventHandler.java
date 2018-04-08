package seeding.ai_db_updater.handlers;

import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.EntityTypeAttribute;
import user_interface.ui_models.attributes.computable_attributes.LapsedAttribute;
import user_interface.ui_models.attributes.computable_attributes.MaintenanceEventAttribute;
import user_interface.ui_models.attributes.computable_attributes.ReinstatedAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * Created by ehallmark on 7/12/17.
 */
public class MaintenanceEventHandler implements LineHandler {
    protected static AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected static LapsedAttribute lapsedAttribute = new LapsedAttribute();
    protected static ReinstatedAttribute reinstatedAttribute = new ReinstatedAttribute();
    protected static EntityTypeAttribute entityTypeAttribute = new EntityTypeAttribute();
    protected static final MaintenanceEventAttribute maintenanceEventAttribute = new MaintenanceEventAttribute();
    private boolean updatePostgres;
    private Consumer<Pair<String,String>> postgresConsumer;
    public MaintenanceEventHandler(Consumer<Pair<String,String>> postgresConsumer) {
        this.updatePostgres=postgresConsumer!=null;
        this.postgresConsumer=postgresConsumer;
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 50) {
            String patNum = line.substring(0, 7);
            String maintenanceCode = line.substring(46, 51).trim();
            if (patNum != null && maintenanceCode != null ) {

                if(updatePostgres) {
                    postgresConsumer.accept(new Pair<>(patNum,maintenanceCode));
                } else {
                    String filing = assetToFilingMap.getPatentDataMap().get(patNum);
                    if (filing != null) {
                        synchronized (maintenanceEventAttribute) {
                            maintenanceEventAttribute.loadMap().putIfAbsent(filing, Collections.synchronizedSet(new HashSet<>()));
                            maintenanceEventAttribute.loadMap().get(filing).add(maintenanceCode);
                        }
                        if (maintenanceCode.equals("EXP.")) {
                            lapsedAttribute.getFilings().add(filing);
                            reinstatedAttribute.getFilings().remove(filing);

                        } else if (maintenanceCode.equals("EXPX")) {
                            // reinstated
                            lapsedAttribute.getFilings().remove(filing);
                            reinstatedAttribute.getFilings().add(filing);

                        } else if (maintenanceCode.equals("REM.")) {
                            // reminder
                        } else if (maintenanceCode.startsWith("M2") || maintenanceCode.startsWith("SM") || maintenanceCode.equals("LTOS") || maintenanceCode.equals("MTOS")) {
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.SMALL).add(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.MICRO).remove(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.LARGE).remove(filing);
                        } else if (maintenanceCode.startsWith("M1") || maintenanceCode.startsWith("LSM")) {
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.LARGE).add(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.SMALL).remove(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.MICRO).remove(filing);
                        } else if (maintenanceCode.startsWith("M3") || maintenanceCode.equals("STOM")) {
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.MICRO).add(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.SMALL).remove(filing);
                            entityTypeAttribute.getTypeToFilingMap().get(Constants.LARGE).remove(filing);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void save() {
        if(!updatePostgres) {
            maintenanceEventAttribute.save();
            lapsedAttribute.save();
            reinstatedAttribute.save();
            entityTypeAttribute.save();
        }
    }

}
