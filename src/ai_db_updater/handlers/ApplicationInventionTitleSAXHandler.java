package ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import ai_db_updater.database.Database;

/**

 */
public class ApplicationInventionTitleSAXHandler extends InventionTitleSAXHandler{
    @Override
    public CustomHandler newInstance() {
        return new ApplicationInventionTitleSAXHandler();
    }

    @Override
    public void save() {
        Database.saveObject(patentToInventionTitleMap,Database.appToInventionTitleMapFile);
        Database.saveObject(patentToOriginalAssigneeMap,Database.appToOriginalAssigneeMapFile);
    }
}