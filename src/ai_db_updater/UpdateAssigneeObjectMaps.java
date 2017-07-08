package ai_db_updater;

import ai_db_updater.database.Database;
import ai_db_updater.handlers.AssignmentSAXHandler;
import tools.AssigneeTrimmer;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/8/2017.
 */
public class UpdateAssigneeObjectMaps {
    public static void main(String[] args) throws Exception {
        // handle maps
        handleMap(Database.patentToOriginalAssigneeMapFile);
        handleMap(Database.appToOriginalAssigneeMapFile);
        handleMap(AssignmentSAXHandler.patentToAssigneeMapFile);

        // run ConstructAssigneeToPatentsMap
        ConstructAssigneeToPatentsMap.main(args);
    }


    private static void handleMap(File file) {
        Map<String,Collection<String>> map = (Map<String,Collection<String>>) Database.loadObject(file);

        map = map.entrySet().stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(assignee-> AssigneeTrimmer.standardizedAssignee(assignee)).collect(Collectors.toList())));

        Database.saveObject(map,file);
    }
}
