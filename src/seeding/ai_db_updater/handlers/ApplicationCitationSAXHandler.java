package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**

 */
public class ApplicationCitationSAXHandler extends CitationSAXHandler {

    private ApplicationCitationSAXHandler(Map<String,LocalDate> patentToPubDateMap, Map<String,LocalDate> patentToAppDateMap, Map<String,LocalDate> patentToPriorityDateMap, Map<String,Set<String>> patentToCitedPatentsMap, Map<String,Set<String>> patentToRelatedDocMap, Set<String> lapsedPatentsSet) {
        super(patentToPubDateMap,patentToAppDateMap,patentToPriorityDateMap,patentToCitedPatentsMap,patentToRelatedDocMap,lapsedPatentsSet);
    }

    public ApplicationCitationSAXHandler() {
        super();
    }

    @Override
    public CustomHandler newInstance() {
        return new ApplicationCitationSAXHandler(patentToPubDateMap,patentToAppDateMap,patentToPriorityDateMap,patentToCitedPatentsMap,patentToRelatedDocMap,lapsedPatentsSet);
    }

    @Override
    public void save() {
        Database.saveObject(patentToCitedPatentsMap,Database.appToCitedPatentsMapFile);
        Database.saveObject(patentToRelatedDocMap,Database.appToRelatedDocMapFile);
        Database.saveObject(patentToPubDateMap,Database.appToPubDateMapFile);
        Database.saveObject(patentToAppDateMap,Database.appToAppDateMapFile);
        Database.saveObject(patentToPriorityDateMap,Database.appToPriorityDateMapFile);
        Database.saveObject(lapsedPatentsSet,Database.lapsedAppSetFile);
    }
}