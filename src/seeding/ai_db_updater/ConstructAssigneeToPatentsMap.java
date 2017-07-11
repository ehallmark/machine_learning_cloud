package seeding.ai_db_updater;

import seeding.Database;
import seeding.ai_db_updater.handlers.AssignmentSAXHandler;

import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 1/23/17.
 */
public class ConstructAssigneeToPatentsMap {
    public static File assigneeToPatentsMapFile = new File("assignee_to_patents_map.jobj");

    public static Map<String,Set<String>> load() throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(assigneeToPatentsMapFile)));
        Map<String,Set<String>> assigneeToPatentsMap = (Map<String,Set<String>>)ois.readObject();
        ois.close();
        return assigneeToPatentsMap;
    }

    public static void constructMap() throws Exception {
        // first load original assignee map and latest assignee map
        System.out.println("Starting to load latest assignee map...");
        Map<String,List<String>> latestAssigneeMap = (Map<String,List<String>>) Database.loadObject(AssignmentSAXHandler.patentToAssigneeMapFile);

        if(latestAssigneeMap==null) throw new RuntimeException("Latest Assignee Map is null");

        System.out.println("Starting to read through latest assignee map...");
        // then merge all into this map
        Map<String,Set<String>> assigneeToPatentsMap = new HashMap<>();
        latestAssigneeMap.forEach((patent,assignees)->{
            assignees.forEach(assignee->{
                if(assigneeToPatentsMap.containsKey(assignee)) {
                    assigneeToPatentsMap.get(assignee).add(patent);
                } else{
                    Set<String> patents = new HashSet<String>();
                    patents.add(patent);
                    assigneeToPatentsMap.put(assignee,patents);
                }
            });
        });

        System.out.println("Starting to load original assignee map...");
        Map<String,List<String>> originalAssigneeMap = (Map<String,List<String>>) Database.loadObject(Database.patentToOriginalAssigneeMapFile);
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
        Database.saveObject(assigneeToPatentsMap,assigneeToPatentsMapFile);

        System.out.println("Num assignees: "+assigneeToPatentsMap.size());
    }

    public static void main(String[] args) throws Exception {
        constructMap();
    }

}
