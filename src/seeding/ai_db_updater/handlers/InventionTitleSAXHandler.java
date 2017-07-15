package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import tools.AssigneeTrimmer;

import java.util.*;

/**

 */
public class InventionTitleSAXHandler extends CustomHandler{
    protected static Map<String,String> patentToInventionTitleMap = Collections.synchronizedMap(new HashMap<>());
    protected  static Map<String,List<String>> patentToOriginalAssigneeMap = Collections.synchronizedMap(new HashMap<>());

    protected boolean inPublicationReference=false;
    protected boolean isDocNumber=false;
    protected boolean isInventionTitle=false;
    protected boolean shouldTerminate = false;
    protected boolean inAssignee=false;
    protected boolean isOrgname = false;
    protected String pubDocNumber;
    protected String inventionTitle;
    protected List<String> documentPieces = new ArrayList<>();
    protected List<String> originalAssignees = new ArrayList<>();

    protected void update() {
        if (pubDocNumber != null&&inventionTitle!=null) {
            patentToInventionTitleMap.put(pubDocNumber,inventionTitle);
        }
        if(pubDocNumber!=null && !originalAssignees.isEmpty()) {
            patentToOriginalAssigneeMap.put(pubDocNumber,originalAssignees);
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new InventionTitleSAXHandler();
    }

    @Override
    public void reset() {
        update();
        isInventionTitle=false;
        inPublicationReference=false;
        isDocNumber=false;
        inAssignee=false;
        isOrgname=false;
        shouldTerminate = false;
        inventionTitle=null;
        pubDocNumber=null;
        documentPieces.clear();
        originalAssignees = new ArrayList<>();
    }

    @Override
    public void save() {
        Database.saveObject(patentToInventionTitleMap,Database.patentToInventionTitleMapFile);
        Database.saveObject(patentToOriginalAssigneeMap,Database.patentToOriginalAssigneeMapFile);
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        if(shouldTerminate) return;

        //System.out.println("Start Element :" + qName);

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equalsIgnoreCase("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equalsIgnoreCase("invention-title")){
            isInventionTitle=true;
        }

        if(qName.toLowerCase().endsWith("assignee")) {
            inAssignee=true;
        }

        if(inAssignee&&qName.equalsIgnoreCase("orgname")) {
            isOrgname=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        if(shouldTerminate) return;

        //System.out.println("End Element :" + qName);

        if(qName.equalsIgnoreCase("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());
            if(pubDocNumber.length()!=11) {
                throw new RuntimeException("Not 11: "+pubDocNumber);
            }
            if(pubDocNumber.isEmpty()) {
                pubDocNumber=null;
                shouldTerminate=true;
            }
            documentPieces.clear();
        }

        if(qName.equalsIgnoreCase("invention-title")){
            isInventionTitle=false;
            inventionTitle=String.join("",documentPieces).trim().toUpperCase().replaceAll("[^A-Z0-9 ]","");

            if(inventionTitle.isEmpty()) {
                inventionTitle=null;
            }
            documentPieces.clear();
        }

        if(qName.equalsIgnoreCase("publication-reference")){
            inPublicationReference=false;
        }

        if(inAssignee&&qName.equalsIgnoreCase("orgname")) {
            isOrgname=false;
            String assignee = AssigneeTrimmer.standardizedAssignee(String.join(" ",documentPieces));
            if(assignee.length()>0) {
                originalAssignees.add(assignee);
            }
            documentPieces.clear();
        }

        if(qName.toLowerCase().endsWith("assignee")) {
            inAssignee=false;
        }

    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isInventionTitle||isDocNumber||isOrgname)){
            documentPieces.add(new String(ch,start,length));
        }

    }
}