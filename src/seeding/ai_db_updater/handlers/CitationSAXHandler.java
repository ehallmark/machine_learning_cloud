package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**

 */
public class CitationSAXHandler extends CustomHandler{
    protected static Map<String,LocalDate> patentToPubDateMap = Collections.synchronizedMap(new HashMap<>());
    protected static Map<String,LocalDate> patentToAppDateMap = Collections.synchronizedMap(new HashMap<>());
    protected static Map<String,Set<String>> patentToCitedPatentsMap = Collections.synchronizedMap(new HashMap<>());
    protected static Map<String,Set<String>> patentToRelatedDocMap = Collections.synchronizedMap(new HashMap());
    protected static Map<String,LocalDate> patentToPriorityDateMap = Collections.synchronizedMap(new HashMap<>());
    protected static Set<String> lapsedPatentsSet = Collections.synchronizedSet(new HashSet<>());

    protected boolean inPublicationReference=false;
    protected boolean inApplicationReference=false;
    protected boolean isDocNumber=false;
    protected boolean isAppDate=false;
    protected boolean isPubDate=false;
    protected boolean inCitation=false;
    protected boolean isRelatedDocNumber=false;
    protected boolean inRelatedDoc=false;
    protected boolean inPriorityClaims=false;
    protected boolean isCitedDocNumber = false;
    protected boolean isPriorityDate = false;
    protected boolean shouldTerminate = false;
    protected boolean isRelatedDocKind = false;
    protected boolean isCitedDocKind = false;
    protected boolean isRelatedDocCountry = false;
    protected boolean isCitedDocCountry = false;
    protected String pubDocNumber;
    protected String docNumber;
    protected String docKind;
    protected LocalDate appDate;
    protected LocalDate pubDate;
    protected LocalDate priorityDate;
    protected List<String> documentPieces = new ArrayList<>();
    protected Set<String> citedDocuments = new HashSet<>();
    protected Set<String> relatedDocuments = new HashSet<>();
    protected String docCountry;

    @Override
    public CustomHandler newInstance() {
        return new CitationSAXHandler();
    }

    private void update() {
        if(pubDocNumber!=null) {
            if (pubDate != null) {
                patentToPubDateMap.put(pubDocNumber, pubDate);
            }
            if (appDate != null) {
                patentToAppDateMap.put(pubDocNumber, appDate);
            }
            if (priorityDate != null) {
                patentToPriorityDateMap.put(pubDocNumber, priorityDate);
                if (priorityDate.plusYears(20).isBefore(LocalDate.now())) {
                    lapsedPatentsSet.add(pubDocNumber);
                }
            }
            if (!citedDocuments.isEmpty()) {
                //System.out.println(patNum+" has "+cited.size()+" cited documents");
                patentToCitedPatentsMap.put(pubDocNumber, citedDocuments);
            }
            if (!relatedDocuments.isEmpty()) {
                //System.out.println(patNum+ " has "+related.size()+" related documents");
                patentToRelatedDocMap.put(pubDocNumber, relatedDocuments);
            }
        }
    }

    public void reset() {
        update();
        isCitedDocNumber=false;
        inPublicationReference=false;
        inApplicationReference=false;
        docCountry=null;
        isRelatedDocCountry=false;
        isCitedDocCountry=false;
        isRelatedDocKind=false;
        isCitedDocKind = false;
        isDocNumber=false;
        isAppDate=false;
        isPubDate=false;
        inCitation=false;
        shouldTerminate = false;
        appDate=null;
        docKind=null;
        docNumber=null;
        pubDate=null;
        inRelatedDoc=false;
        inPriorityClaims=false;
        isRelatedDocNumber=false;
        isPriorityDate=false;
        priorityDate=null;
        pubDocNumber=null;
        documentPieces.clear();
        citedDocuments = new HashSet<>();
        relatedDocuments = new HashSet<>();
    }

    @Override
    public void save() {
        // invert patent map to get referenced by instead of referencing
        Map<String,Set<String>> patentToReferencedByMap = Collections.synchronizedMap(new HashMap<>());
        patentToCitedPatentsMap.forEach((patent,citedSet)->{
            citedSet.forEach(cited-> {
                if (patentToReferencedByMap.containsKey(cited)) {
                    patentToReferencedByMap.get(cited).add(patent);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(patent);
                    patentToReferencedByMap.put(cited, set);
                }
            });
        });

        // date to patent map
        Map<LocalDate,Set<String>> pubDateToPatentMap = Collections.synchronizedMap(new HashMap<>());
        patentToPubDateMap.forEach((patent,pubDate)->{
            if(pubDateToPatentMap.containsKey(pubDate)) {
                pubDateToPatentMap.get(pubDate).add(patent);
            } else {
                Set<String> set = new HashSet<>();
                set.add(patent);
                pubDateToPatentMap.put(pubDate,set);
            }
        });


        Database.saveObject(patentToCitedPatentsMap,Database.patentToCitedPatentsMapFile);
        Database.saveObject(pubDateToPatentMap,Database.pubDateToPatentMapFile);
        Database.saveObject(patentToRelatedDocMap,Database.patentToRelatedDocMapFile);
        Database.saveObject(patentToReferencedByMap,Database.patentToReferencedByMapFile);
        Database.saveObject(patentToAppDateMap,Database.patentToAppDateMapFile);
        Database.saveObject(patentToPubDateMap,Database.patentToPubDateMapFile);
        Database.saveObject(patentToPriorityDateMap,Database.patentToPriorityDateMapFile);
        Database.saveObject(lapsedPatentsSet,Database.lapsedPatentsSetFile);
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        if(shouldTerminate) return;

        //System.out.println("Start Element :" + qName);

        if(qName.equals("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equals("application-reference")) {
            inApplicationReference=true;
        }

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equals("date")&&inPublicationReference){
            isPubDate=true;
        }

        if(qName.equals("priority-claims")) {
            inPriorityClaims=true;
        }

        if(inPriorityClaims&&qName.equals("date")) {
            isPriorityDate=true;
        }

        if(qName.equals("date")&&inApplicationReference){
            isAppDate=true;
        }

        if(qName.equals("doc-number")&&inCitation) {
            isCitedDocNumber=true;
        }

        if(qName.equals("kind")&&inCitation) {
            isCitedDocKind=true;
        }

        if(qName.equals("patcit")) {
            inCitation=true;
        }

        if(inRelatedDoc&&qName.equals("doc-number")) {
            isRelatedDocNumber=true;
        }

        if(inRelatedDoc&&qName.equals("kind")) {
            isRelatedDocKind=true;
        }

        if(inRelatedDoc&&qName.equals("country")) {
            isRelatedDocCountry=true;
        }

        if(inCitation&&qName.equals("country")) {
            isCitedDocCountry=true;
        }

        if(qName.contains("related-doc")||qName.equals("relation")||qName.equals("us-relation")) {
            inRelatedDoc=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        if(shouldTerminate) return;
        //System.out.println("End Element :" + qName);

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());
            if(pubDocNumber.isEmpty()) {
                pubDocNumber=null;
                shouldTerminate=true;
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inPriorityClaims){
            isPriorityDate=false;
            try {
                LocalDate date = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
                if(priorityDate==null||(date.isBefore(priorityDate))) {
                    priorityDate=date;
                }
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inPublicationReference){
            isPubDate=false;
            try {
                pubDate = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("date")&&inApplicationReference){
            isAppDate=false;
            try {
                appDate = LocalDate.parse(String.join("", documentPieces).trim(), DateTimeFormatter.BASIC_ISO_DATE);
                if(priorityDate == null || appDate.isBefore(priorityDate)) priorityDate=appDate;
            } catch(Exception dateException) {
            }
            documentPieces.clear();
        }

        if(qName.equals("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equals("patcit")) {
            inCitation=false;
            if(docNumber!=null&&docKind!=null&&docCountry!=null) {
                docNumber = handleOtherDoc(docNumber,docKind,docCountry);
                if(docNumber!=null) citedDocuments.add(docNumber);
                docNumber=null;
                docCountry=null;
                docKind=null;
            }
        }

        if(qName.equals("application-reference")) {
            inApplicationReference=false;
        }

        if(qName.equals("doc-number")&&inCitation) {
            isCitedDocNumber=false;
            docNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(inCitation&&qName.equals("kind")) {
            isCitedDocKind=false;
            docKind = String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(inRelatedDoc&&qName.equals("doc-number")) {
            isRelatedDocNumber=false;
            docNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(inRelatedDoc&&qName.equals("kind")) {
            isRelatedDocKind=false;
            docKind = String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(inCitation&&qName.equals("country")) {
            isCitedDocCountry=false;
            docCountry = String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(inRelatedDoc&&qName.equals("country")) {
            isRelatedDocCountry=false;
            docCountry = String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            documentPieces.clear();
        }

        if(qName.contains("related-doc")||qName.equals("relation")||qName.equals("us-relation")) {
            inRelatedDoc=false;
            if(docNumber!=null&&docKind!=null&&docCountry!=null) {
                docNumber = handleOtherDoc(docNumber,docKind,docCountry);
                if(docNumber!=null) relatedDocuments.add(docNumber);
                docNumber=null;
                docKind=null;
                docCountry=null;
            }
        }
    }

    private static String handleOtherDoc(String docNumber, String docKind, String docCountry) {
        if(docNumber.length()<=6 && docCountry.equals("US")) return null;
        if(docCountry.isEmpty()) return null;

        if(docCountry.equals("US")) {
            if ((docKind.startsWith("A")||docKind.isEmpty()) && docNumber.length() == 8) {
                // 23/352355 formatting
                docNumber = docNumber.substring(0, docNumber.length() - 6) + "/" + docNumber.substring(docNumber.length() - 6);
            } else if (docKind.startsWith("B")) {
                if (docNumber.startsWith("0")) docNumber = docNumber.substring(1, docNumber.length());
            } else if(docNumber.length()!=11 && !docNumber.startsWith("20")) {
                return null;
            }
        }
        if(! docCountry.equals("US") && !docNumber.startsWith(docCountry)) docNumber=docCountry+docNumber;
        return docNumber;
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&(isCitedDocNumber||isRelatedDocKind||isRelatedDocCountry||isCitedDocCountry||isCitedDocKind||isDocNumber||isPriorityDate||isRelatedDocNumber||isAppDate||isPubDate)){
            documentPieces.add(new String(ch,start,length));
        }

    }
}