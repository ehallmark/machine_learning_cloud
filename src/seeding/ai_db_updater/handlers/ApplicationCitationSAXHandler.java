package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;

/**

 */
public class ApplicationCitationSAXHandler extends CitationSAXHandler {
    @Override
    public CustomHandler newInstance() {
        return new ApplicationCitationSAXHandler();
    }

    @Override
    public void save() {
        //Database.saveObject(patentToCitedPatentsMap,Database.appToCitedPatentsMapFile);
        Database.saveObject(patentToRelatedDocMap,Database.appToRelatedDocMapFile);
        Database.saveObject(patentToPubDateMap,Database.appToPubDateMapFile);
        Database.saveObject(patentToAppDateMap,Database.appToAppDateMapFile);
        Database.saveObject(patentToPriorityDateMap,Database.appToPriorityDateMapFile);
        Database.saveObject(lapsedPatentsSet,Database.lapsedAppsSetFile);
    }
}