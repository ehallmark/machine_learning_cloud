package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;

import java.util.List;
import java.util.Map;

/**

 */
public class ApplicationInventionTitleSAXHandler extends InventionTitleSAXHandler{

    protected ApplicationInventionTitleSAXHandler(Map<String,String> patentToInventionTitleMap, Map<String,List<String>> patentToOriginalAssigneeMap) {
        super(patentToInventionTitleMap,patentToOriginalAssigneeMap);
    }

    public ApplicationInventionTitleSAXHandler() {
        super();
    }

    @Override
    public CustomHandler newInstance() {
        return new ApplicationInventionTitleSAXHandler(patentToInventionTitleMap,patentToOriginalAssigneeMap);
    }

    @Override
    public void save() {
        Database.saveObject(patentToInventionTitleMap,Database.appToInventionTitleMapFile);
        Database.saveObject(patentToOriginalAssigneeMap,Database.appToOriginalAssigneeMapFile);
    }
}