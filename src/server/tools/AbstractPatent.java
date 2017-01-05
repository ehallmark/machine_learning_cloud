package server.tools;

import seeding.Database;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent implements Comparable<AbstractPatent>, ExcelWritable{
    private static Map<String,String> standardizedAssigneeMap = new HashMap<>();
    private String name;
    private double similarity;

    @Override
    public String[] getDataAsRow(boolean valuePrediction, int tagLimit) {
        if(valuePrediction) {
            return new String[]{
                    name,
                    String.valueOf(similarity),
                    String.valueOf(gatherValue),
                    getFullAssignee(),
                    String.valueOf(Math.min(tagLimit,getTags().size())),
                    getOrderedTags().isEmpty() ? "" : getOrderedTags().get(0),
                    getTags().size() > 1 ? String.join("; ", getOrderedTags().subList(1, Math.min(tagLimit,getOrderedTags().size()))) : "",
                    title
            };
        } else {
            return new String[]{
                    name,
                    String.valueOf(similarity),
                    getFullAssignee(),
                    String.valueOf(Math.min(tagLimit,getTags().size())),
                    getOrderedTags().isEmpty() ? "" : getOrderedTags().get(0),
                    getTags().size() > 1 ? String.join("; ", getOrderedTags().subList(1, Math.min(tagLimit,getOrderedTags().size()))) : "",
                    title
            };
        }
    }

    private Double gatherValue;
    private Map<String,Double> tags;
    private List<String> orderedTags;
    private String title;
    private boolean collateral;
    private String assignee;
    private String collateralAgent;
    private static List<String> suffixes = Arrays.asList(" co"," corp"," corps"," corporation"," incorporated"," ltd", " limited", " inc", " co ltd", " llc");

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
            standardizedAssigneeMap.put(entry.getKey().toLowerCase(),entry.getValue().toLowerCase());
            standardizedAssigneeMap.put(entry.getKey().toUpperCase(),entry.getValue().toUpperCase());
        }
    }

    public AbstractPatent(String name, double similarity, String referringName) throws SQLException {
        this.name = name;
        this.tags = new HashMap<>();
        this.similarity=similarity;
        this.title= Database.getTitleFromDB(name);
        this.assignee = Database.getAssigneeFromDB(name).replaceAll("\\.","").toLowerCase().trim();


        // check if this assignee has a lien
        int collateralIdx = this.assignee.lastIndexOf(" collateral");
        if(collateralIdx<0) collateralIdx = this.assignee.lastIndexOf(" administrative");
        if(collateralIdx<0) collateralIdx = this.assignee.lastIndexOf(" na,");
        if(collateralIdx<0) collateralIdx = this.assignee.lastIndexOf(" na ");
        if(collateralIdx<0 && this.assignee.endsWith(" na")) collateralIdx = this.assignee.lastIndexOf(" na");
        if(collateralIdx>0) {
            collateral=true;
            int idx;
            if(this.assignee.lastIndexOf(" na ")>0 && this.assignee.lastIndexOf(" na ")<collateralIdx) idx = this.assignee.lastIndexOf(" na ");
            else if(this.assignee.lastIndexOf(" na,")>0 && this.assignee.lastIndexOf(" na,")<collateralIdx) idx = this.assignee.lastIndexOf(" na,");
            else if(this.assignee.lastIndexOf(" as ")>0 && this.assignee.lastIndexOf(" as ")<collateralIdx) idx = this.assignee.lastIndexOf(" as ");
            else idx = collateralIdx;
            collateralAgent = this.assignee.substring(0,idx).replaceAll(",","").replaceAll("\\.","").trim();
            this.assignee = Database.selectAssigneeNameFromPatentGrant(name).replaceAll("\\.","").toLowerCase().trim();
        } else {
            collateral=false;
        }

        this.assignee = this.assignee.replaceAll(",","");
        // make sure not the same
        if(collateral&&(collateralAgent.contains(this.assignee)||this.assignee.contains(collateralAgent))) {
            collateral=false;
            assignee = "";
            collateralAgent=null;
        }

        // remove suffixes
        boolean hasSuffixProblem = true;
        while(hasSuffixProblem) {
            if(this.assignee.length()<=6) break;
            hasSuffixProblem = false;
            for (String suffix : suffixes) {
                if (this.assignee.endsWith(suffix)) {
                    hasSuffixProblem = true;
                    this.assignee=this.assignee.substring(0,this.assignee.length()-suffix.length());
                }
            }
        }

        // check for standardized assignee name
        if(standardizedAssigneeMap.containsKey(this.assignee)) {
            assignee=standardizedAssigneeMap.get(this.assignee);
        }
        tags.put(referringName,similarity);
    }

    public String getCollateralAgent() {
        return collateralAgent;
    }

    public void setSimilarity(double sim) {
        similarity=sim;
    }

    public void appendTags(Map<String,Double> newTags) {
        newTags.entrySet().forEach(e -> {
            if (tags.containsKey(e.getKey())) {
                tags.put(e.getKey(), Math.max(e.getValue(), tags.get(e.getKey())));
            } else {
                tags.put(e.getKey(), e.getValue());
            }
        });
        //calculateOrderedTags();
    }

    public void setTags(Map<String,Double> tags) {
        this.tags=tags;
    }

    public void setGatherValue(double value) {
        this.gatherValue=value;
    }

    public Double getGatherValue() {
        return gatherValue;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }

    public Map<String,Double> getTags() {
        return tags;
    }

    private void calculateOrderedTags() {
        orderedTags = tags.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e1->e1.getKey()).collect(Collectors.toList());
    }

    public List<String> getOrderedTags() {
        if(orderedTags==null) calculateOrderedTags();
        return orderedTags;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getFullAssignee() {
        if(collateral&&collateralAgent!=null) {
            return assignee+" subject to lien ("+collateralAgent+")";
        } else {
            return assignee;
        }
    }

    public void flipSimilarity() { similarity*=-1.0; }

    @Override
    public int compareTo(AbstractPatent o) {
        return Double.compare(similarity, o.similarity);
    }
}
