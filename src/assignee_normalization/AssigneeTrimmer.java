package assignee_normalization;

import seeding.Database;

import java.util.*;

/**
 * Created by Evan on 1/22/2017.
 */
public class AssigneeTrimmer {
    private static List<String> suffixes = Arrays.asList(" CO"," CORP"," CORPS"," CORPORATION"," LLP", " CO.", " I", " II", " III", " IV", " V", " AG", " AB", " OY"," INCORPORATED"," LTD", " LIMITED", " INC", " CO LTD", " LLC");
    private static Map<String,String> standardizedAssigneeMap = new HashMap<>();


    public static String standardizedAssignee(String assignee) {
        if(assignee==null)return null;
        assignee=cleanAssignee(assignee);

       /* // remove suffixes
        boolean hasSuffixProblem = true;
        while(hasSuffixProblem) {
            if(assignee.length()<=6) break;
            hasSuffixProblem = false;
            for (String suffix : suffixes) {
                if (assignee.endsWith(suffix)) {
                    hasSuffixProblem = true;
                    assignee=assignee.substring(0,assignee.length()-suffix.length());
                }
            }
        }

        // check for standardized assignee name
        if(standardizedAssigneeMap.containsKey(assignee)) {
            assignee=standardizedAssigneeMap.get(assignee);
        }*/
        return assignee;
    }

    public static String cleanAssignee(String toExtract) {
        toExtract = toExtract.toUpperCase().replaceAll("\\s+"," ");
        while(toExtract.contains("   ")) toExtract=toExtract.replaceAll("   "," "); // strip triple spaces (might be faster)
        while(toExtract.contains("  ")) toExtract=toExtract.replaceAll("  "," "); // strip double spaces
        return toExtract.trim();
    }
}
