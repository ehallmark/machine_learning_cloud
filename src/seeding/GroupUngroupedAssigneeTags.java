package seeding;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/**
 * Created by ehallmark on 10/15/16.
 */
public class GroupUngroupedAssigneeTags {
    public static void main(String[] args) throws Exception {
        File corruptFile = new File("corrupt_file.xls");

        int assigneeIdx = 0;
        int classificationIdx = 1;
        int offset = 1;
        int classificationOffset = 1;
        System.out.println("Reading assignees...");
        List<String> duplicativeAssignees = GetEtsiPatentsList.getExcelList(corruptFile,assigneeIdx,offset);
        for(String assignee : duplicativeAssignees) {
            System.out.println(assignee);
        }
        System.out.println("Reading tags...");
        List<String> associatedTags = GetEtsiPatentsList.getExcelList(corruptFile,classificationIdx,offset);
        for(String tag : associatedTags) {
            System.out.println(tag);
        }

        assert duplicativeAssignees.size()==associatedTags.size() : "Invalid file";

        Map<String,Set<String>> assigneeTagMap = new HashMap<>();
        // regroup stuff properly
        for(int i = 0; i < duplicativeAssignees.size(); i++) {
            String assignee = duplicativeAssignees.get(i);
            String tag = associatedTags.get(i);
            if(assigneeTagMap.containsKey(assignee)) {
                assigneeTagMap.get(assignee).add(tag);
            } else {
                Set<String> set = new HashSet<>();
                set.add(tag);
                assigneeTagMap.put(assignee, set);
            }
        }

        File newFileCsv = new File("fixed_file.csv");
        BufferedWriter bw = new BufferedWriter(new FileWriter(newFileCsv));
        assigneeTagMap.entrySet().forEach(e->{
            try {
                StringJoiner line = new StringJoiner(",","","\n");
                line.add(e.getKey());
                line.add(String.valueOf(e.getValue().size()));
                line.add(String.join("; ", e.getValue()));
                bw.write(line.toString());
                bw.flush();
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        });

        bw.flush();
        bw.close();
    }
}
