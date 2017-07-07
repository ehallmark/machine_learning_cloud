package ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import ai_db_updater.database.Database;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import tools.AssigneeTrimmer;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**

 */
public class AssignmentSAXHandler extends CustomHandler{
    private boolean inPatentAssignment = false;
    private boolean isConveyanceText=false;
    private boolean inPatentAssignee=false;
    private boolean isDocKind = false;
    private boolean isName=false;
    private boolean inDocumentID=false;
    private boolean isDocNumber=false;
    private boolean inPatentAssignor=false;
    private boolean isAssignorsInterest=false;
    boolean shouldTerminate = false;
    private List<String>documentPieces=new ArrayList<>();
    private List<String> currentPatents = new ArrayList<>();
    private List<String> currentAssignees = new ArrayList<>();
    private List<String> currentAssignors = new ArrayList();
    private String docKind=null;
    private String currentPatent = null;
    public static final File patentToAssigneeMapFile = new File("patent_to_assignee_map_latest.jobj");
    private static final Map<String,List<String>> patentToAssigneeMap = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String,Integer> assigneeToAssetsSoldCountMap = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String,Integer> assigneeToAssetsPurchasedCountMap = Collections.synchronizedMap(new HashMap<>());
    private static final File assigneeToAssetsSoldCountMapFile = new File("assignee_to_assets_sold_count_map.jobj");
    private static final File assigneeToAssetsPurchasedCountMapFile = new File("assignee_to_assets_purchased_count_map.jobj");


    public static Map<String,List<String>> load() {
        return (Map<String,List<String>>) Database.loadObject(patentToAssigneeMapFile);
    }

    public void save() {
        Database.saveObject(assigneeToAssetsSoldCountMap,assigneeToAssetsSoldCountMapFile);
        Database.saveObject(assigneeToAssetsPurchasedCountMap,assigneeToAssetsPurchasedCountMapFile);
        Database.saveObject(patentToAssigneeMap,patentToAssigneeMapFile);
    }

    @Override
    public CustomHandler newInstance() {
        return new AssignmentSAXHandler();
    }

    public void reset() {
        // DO NOT CLEAR PATENT TO ASSIGNEE MAP!!!!
        inPatentAssignment=false;
        isConveyanceText=false;
        inPatentAssignee=false;
        currentPatent=null;
        docKind=null;
        isDocNumber=false;
        inDocumentID=false;
        inPatentAssignor=false;
        isDocKind=false;
        isName=false;
        shouldTerminate = false;
        currentAssignors.clear();
        currentAssignees.clear();
        isAssignorsInterest=false;
        currentPatents.clear();
        documentPieces.clear();
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        if(qName.equals("patent-assignment")){
            shouldTerminate=false;
            inPatentAssignment=true;
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=true;
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=true;
        }
        if(inDocumentID&&qName.equals("kind")){
            isDocKind=true;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = true;
        }

        if(inPatentAssignment&&qName.equals("patent-assignee")) {
            inPatentAssignee=true;
        }

        if(inPatentAssignment&&qName.equals("patent-assignor")) {
            inPatentAssignor=true;
        }

        if((inPatentAssignee||inPatentAssignor)&&qName.equals("name")) {
            isName=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{


        if(qName.equals("patent-assignment")){
            inPatentAssignment=false;
            // done with patent so update patent map and reset data
            if(!shouldTerminate&&!currentAssignees.isEmpty()) {
                List<String> dupAssignees = new ArrayList<>(currentAssignees.size());
                dupAssignees.addAll(currentAssignees);
                AtomicInteger patentCount = new AtomicInteger(0);
                for(int i = 0; i < currentPatents.size(); i++) {
                    String patent = currentPatents.get(i);
                    if(patent.startsWith("0"))patent=patent.replaceFirst("0","");
                    try {
                        if(Integer.valueOf(patent) > 6000000){
                            patentToAssigneeMap.put(patent, dupAssignees);
                            patentCount.getAndIncrement();
                        }
                    } catch(Exception e) {

                    }
                }
                if(patentCount.get()>0 && isAssignorsInterest) {
                    currentAssignees.forEach(assignee->{
                        if(assigneeToAssetsPurchasedCountMap.containsKey(assignee)) {
                            assigneeToAssetsPurchasedCountMap.put(assignee,assigneeToAssetsPurchasedCountMap.get(assignee)+patentCount.get());
                        } else {
                            assigneeToAssetsPurchasedCountMap.put(assignee,patentCount.get());
                        }
                    });
                    currentAssignors.forEach(assignor->{
                        if(assigneeToAssetsSoldCountMap.containsKey(assignor)) {
                            assigneeToAssetsSoldCountMap.put(assignor,assigneeToAssetsSoldCountMap.get(assignor)+patentCount.get());
                        } else {
                            assigneeToAssetsSoldCountMap.put(assignor,patentCount.get());
                        }
                    });
                }
            }
            reset();
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=false;
            String text = AssigneeTrimmer.cleanAssignee(String.join("",documentPieces));
            boolean changeOfNameOrAssignorsInterest = false;
            if(text.startsWith("ASSIGNMENT OF ASSIGN")) {
                isAssignorsInterest=true;
                changeOfNameOrAssignorsInterest=true;
            }
            if(text.startsWith("CHANGE OF NAME")) changeOfNameOrAssignorsInterest=true;
            if(text==null||text.length()==0||!changeOfNameOrAssignorsInterest) {
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=false;
            if(docKind!=null&&docKind.startsWith("B")) {
                currentPatents.add(currentPatent);
            }
            docKind=null;
            currentPatent=null;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = false;
            currentPatent = AssigneeTrimmer.cleanAssignee(String.join("",documentPieces));
            documentPieces.clear();
        }

        if(inPatentAssignment&&qName.equals("patent-assignee")) {
            inPatentAssignee=false;
        }

        if(inPatentAssignment&&qName.equals("patent-assignor")) {
            inPatentAssignor=false;
        }

        if(inPatentAssignor&&qName.equals("name")) {
            isName=false;
            String text = AssigneeTrimmer.standardizedAssignee(String.join("",documentPieces));
            if(text!=null&&text.length()>0) {
                currentAssignors.add(text);
            }
            documentPieces.clear();
        }

        if(inPatentAssignee&&qName.equals("name")) {
            isName=false;
            String text = AssigneeTrimmer.standardizedAssignee(String.join("",documentPieces));
            if(text!=null&&text.length()>0) {
                currentAssignees.add(text);
            }
            documentPieces.clear();
        }

        if(inDocumentID&&qName.equals("kind")){
            isDocKind=false;
            String text = (String.join("",documentPieces)).trim().toUpperCase();
            if(text!=null&&text.length()>0) {
                docKind=text;
            }
            documentPieces.clear();
        }

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        if((!shouldTerminate)&&(isName||isDocNumber||isDocKind||isConveyanceText)){
            documentPieces.add(new String(ch,start,length));
        }

    }

}