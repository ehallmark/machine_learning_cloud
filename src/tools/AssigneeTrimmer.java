package tools;

import seeding.Database;

import java.util.*;

/**
 * Created by Evan on 1/22/2017.
 */
public class AssigneeTrimmer {
    private static List<String> suffixes = Arrays.asList(" CO"," CORP"," CORPS"," CORPORATION"," INCORPORATED"," LTD", " LIMITED", " INC", " CO LTD", " LLC");
    private static Map<String,String> standardizedAssigneeMap = new HashMap<>();

    // ASSIGNEE NAME STANDARDIZATION MAP CREATION HERE
    static {
        String defaultATT = "AT & T CORPORATION";
        String defaultNokia = "NOKIA CORPORATION";
        String defaultAmazon = "AMAZON TECHNOLOGIES INC";
        String defaultSalesforce = "SALESFORCE";
        String defaultVerizon = "VERIZON PATENT AND LICENSING INC";
        String defaultLenovo = "LENOVO";
        String defaultMicrosoft = "MICROSOFT CORPORATION";

        Map<String,String> tmp = new HashMap<>();
        tmp.put("MOTOROLA MOBILITY LLC",defaultLenovo);
        tmp.put("LINKEDIN CORPORATION",defaultMicrosoft);
        tmp.put("TELEFONAKTIEBOLAGET LM ERICSSON (PUBL)",
                "TELEFONAKTIEBOLAGET L M ERICSSON (PUBL)");
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP",defaultATT);
        tmp.put("AT & T MOBILITY II LLC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP",defaultATT);
        tmp.put("AT & T KNOWLEDGE VENTURES LP",defaultATT);
        tmp.put("AT & T SERVICES INC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP A NEVADA PARTNERSHIP",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY ILP",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY II LP A NEVADA LIMITED PARTNERSHIP",defaultATT);
        tmp.put("AT & T INVESTMENTS UK LLC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP (FORMERLY KNOWN AS SBC KNOWLEDGE VENTURES LP)",defaultATT);
        tmp.put("AT & T WIRELESS SERVICES INC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY INC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP F/K/A AT & T KNOWLEDGE VENTURES LP",defaultATT);
        tmp.put("AT & T CORP",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY 1 LP",defaultATT);
        tmp.put("AT & T KNOWLEDGE VENTURES LP A NEVADA PARTNERSHIP",defaultATT);
        tmp.put("AT & T BLS INTELLECTUAL PROPERTY INC",defaultATT);
        tmp.put("AT & T MOBILITY IP LLC",defaultATT);
        tmp.put("AT & T LABS INC",defaultATT);
        tmp.put("AT & T DELAWARE INTELLECTUAL PROPERTY INC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY II LP",defaultATT);
        tmp.put("AT & T LABORATORIES-CAMBRIDGE LTD",defaultATT);
        tmp.put("AT & T KNOWLEGE VENTURES LP",defaultATT);
        tmp.put("AT & T MOBILITY II LLC F/K/A CINGULAR WIRELESS LLC",defaultATT);
        tmp.put("AT & T INTELLECTUAL PROPERTY I LP A NEVADA LIMITED PARTNERSHIP",defaultATT);
        tmp.put("AT & T MOBILITY LLC",defaultATT);
        tmp.put("BELLSOUTH INTELLECTUAL PROPERTY CORPORATION",defaultATT);
        tmp.put("EXCALIBUR IP LLC","EXCALIBUR IP LLC (YAHOO)");
        tmp.put("NOKIA TECHNOLOGIES OY", defaultNokia);
        tmp.put("NOKIA SOLUTIONS AND NETWORKS OY", defaultNokia);
        tmp.put("NOKIA SOLUTIONS AND NETWORKS GMBH & CO KG", defaultNokia);
        tmp.put("NOKIA CORPORATION INC", "NOKIA CORPORATION");
        tmp.put("NOKIA SIEMENS NETWORKS GMBH & CO KG", defaultNokia);
        tmp.put("NOKIA SIEMENS NETWORKS GMBH & CO KG", defaultNokia);
        tmp.put("NOKIA SIEMENS NETWORKS OY", defaultNokia);
        tmp.put("ALCATEL LUCENT", defaultNokia);
        tmp.put("ALCATEL-LUCENT USA INC", defaultNokia);
        tmp.put("ALCATEL-LUCENT CANADA INC", defaultNokia);
        tmp.put("ALCATEL", defaultNokia);
        tmp.put("ALCATEL LUCENT (SUCCESSOR IN INTEREST TO ALCATEL-LUCENT NV)", defaultNokia);
        tmp.put("ALCATEL-LUCENT", defaultNokia);
        tmp.put("ALCATEL-LUCENT USA", defaultNokia);
        tmp.put("AMAZONCOM INC",defaultAmazon);
        tmp.put("AMAZON TECHONOLOGIES INC",defaultAmazon);
        tmp.put("SALESFORCECOM",defaultSalesforce);
        tmp.put("CELLCO PARTNERSHIP D/B/A VERIZON WIRELESS",defaultVerizon);
        tmp.put("VERIZON DIGITAL MEDIA SERVICES INC",defaultVerizon);
        tmp.put("VERIZON BUSINESS GLOBAL LLC",defaultVerizon);
        tmp.put("VERIZON DATA SERVICES LLC",defaultVerizon);
        tmp.put("CELLCO PARTNERSHIP (D/B/A VERIZON WIRELESS)",defaultVerizon);
        tmp.put("VERIZON TELEMATICS INC",defaultVerizon);
        tmp.put("VERIZON PATENT LICENSING INC",defaultVerizon);
        tmp.put("VERIZON LABORATORIES INC",defaultVerizon);
        tmp.put("VERIZON CORPORATE SERVICES GROUP INC",defaultVerizon);
        tmp.put("VERIZON DEUTSCHLAND GMBH",defaultVerizon);
        tmp.put("CELLCO PARTNERSHIP D/B/A VERIZON WIRELESSS",defaultVerizon);
        tmp.put("VERIZON PATENT AND LISCENSING INC",defaultVerizon);

        // lower case each string just in case
        Set<Map.Entry<String,String>> entries = tmp.entrySet();
        for(Map.Entry<String,String> entry: entries) {
            standardizedAssigneeMap.put(cleanAssignee(entry.getKey()),cleanAssignee(entry.getValue()));
        }
    }

    public static String standardizedAssignee(String assignee) {
        if(assignee==null)return null;
        assignee=cleanAssignee(assignee);

        // remove suffixes
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
        }
        return assignee;
    }

    private static String cleanAssignee(String toExtract) {
        String data = toExtract.toUpperCase().replaceAll("[^A-Z0-9 ]","");
        while(data.contains("   ")) data=data.replaceAll("   "," "); // strip triple spaces (might be faster)
        while(data.contains("  ")) data=data.replaceAll("  "," "); // strip double spaces
        return data.trim();
    }
}
