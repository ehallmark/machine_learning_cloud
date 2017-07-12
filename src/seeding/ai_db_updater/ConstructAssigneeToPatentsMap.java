package seeding.ai_db_updater;

import seeding.Database;
import seeding.ai_db_updater.handlers.AssignmentSAXHandler;

import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/23/17.
 */
public class ConstructAssigneeToPatentsMap {
    public static void constructMap(File latestAssigneeMapFile, File originalAssigneeMapFile, File newFile) throws Exception {
        // first load original assignee map and latest assignee map
        Map<String, Set<String>> assigneeToPatentsMap = new HashMap<>();
        Map<String, List<String>> latestAssigneeMap;
        if(latestAssigneeMapFile!=null) {
            System.out.println("Starting to load latest assignee map...");
            latestAssigneeMap = (Map<String, List<String>>) Database.loadObject(latestAssigneeMapFile);

            System.out.println("Starting to read through latest assignee map...");
            // then merge all into this map
            latestAssigneeMap.forEach((patent, assignees) -> {
                assignees.forEach(assignee -> {
                    if (assigneeToPatentsMap.containsKey(assignee)) {
                        assigneeToPatentsMap.get(assignee).add(patent);
                    } else {
                        Set<String> patents = new HashSet<String>();
                        patents.add(patent);
                        assigneeToPatentsMap.put(assignee, patents);
                    }
                });
            });
        } else {
            latestAssigneeMap = new HashMap<>();
        }

        System.out.println("Starting to load original assignee map...");
        Map<String,List<String>> originalAssigneeMap = (Map<String,List<String>>) Database.loadObject(originalAssigneeMapFile);
        if(originalAssigneeMap==null) throw new RuntimeException("Original Assignee Map is null");

        System.out.println("Starting to read through original assignee map...");
        originalAssigneeMap.forEach((patent,assignees)->{
            // skip if latestAssigneeMap has the patent
            if(!latestAssigneeMap.containsKey(patent)) {
                assignees.forEach(assignee -> {
                    if (assigneeToPatentsMap.containsKey(assignee)) {
                        assigneeToPatentsMap.get(assignee).add(patent);
                    } else {
                        Set<String> patents = new HashSet<>();
                        patents.add(patent);
                        assigneeToPatentsMap.put(assignee, patents);
                    }
                });
            }
        });

        System.out.println("Starting to save results...");
        // save
        Database.saveObject(assigneeToPatentsMap,newFile);

        System.out.println("Num assignees: "+assigneeToPatentsMap.size());
    }

    public static void main(String[] args) throws Exception {
        constructMap(Database.patentToLatestAssigneeMapFile,Database.patentToOriginalAssigneeMapFile,Database.assigneeToPatentsMapFile);
        constructMap(null,Database.appToOriginalAssigneeMapFile,Database.assigneeToAppsMapFile);
    }

}
