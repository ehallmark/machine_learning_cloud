package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import seeding.Database;
import tools.AssigneeTrimmer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;

/**

 */
public class TransactionSAXHandler extends CustomHandler{
    private boolean inPatentAssignment = false;
    private boolean isConveyanceText=false;
    private boolean isAssignorsInterest = false;
    private boolean isSecurityInterest = false;
    private boolean inDocumentID=false;
    private boolean isDocNumber=false;
    boolean shouldTerminate = false;
    private List<String> documentPieces=new ArrayList<>();
    private List<String> currentPatents = new ArrayList<>();

    private static final File patentToSecurityInterestCountMapFile = new File("patent_to_security_interest_count_map.jobj");
    private static Map<String,Integer> patentToSecurityInterestCountMap = new HashMap<>();
    private static final File patentToTransactionSizesMapFile = new File("patent_to_transaction_sizes_map.jobj");
    private static Map<String,List<Integer>> patentToTransactionSizeMap = new HashMap<>();

    public void save()  {
        Database.saveObject(patentToSecurityInterestCountMap,patentToSecurityInterestCountMapFile);
        Database.saveObject(patentToTransactionSizeMap,patentToTransactionSizesMapFile);
    }

    @Override
    public CustomHandler newInstance() {
        return new TransactionSAXHandler();
    }

    public void reset() {
        inPatentAssignment=false;
        isConveyanceText=false;
        isAssignorsInterest=false;
        isSecurityInterest=false;
        isDocNumber=false;
        inDocumentID=false;
        shouldTerminate = false;
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

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        if(qName.equals("patent-assignment")){
            inPatentAssignment=false;
            // done with patent so update patent map and reset data
            if(!shouldTerminate&&!currentPatents.isEmpty()) {
                for(int i = 0; i < currentPatents.size(); i++) {
                    String patent = currentPatents.get(i);
                    try {
                        if(Integer.valueOf(patent) >= 6000000) {
                            // good to go
                            if(isAssignorsInterest) {
                                // transaction
                                if(patentToTransactionSizeMap.containsKey(patent)) {
                                    patentToTransactionSizeMap.get(patent).add(currentPatents.size());
                                } else {
                                    List<Integer> sizes = new ArrayList<>();
                                    sizes.add(currentPatents.size());
                                    patentToTransactionSizeMap.put(patent,sizes);
                                }
                            } else if (isSecurityInterest) {
                                if(patentToSecurityInterestCountMap.containsKey(patent)) {
                                    patentToSecurityInterestCountMap.put(patent,patentToSecurityInterestCountMap.get(patent)+1);
                                } else {
                                    patentToSecurityInterestCountMap.put(patent,1);
                                }
                            }
                        }
                    } catch (NumberFormatException nfe) {
                        // not a utility patent
                    }
                }
            }
            reset();
        }

        if(inPatentAssignment&&qName.equals("conveyance-text")){
            isConveyanceText=false;
            String text = AssigneeTrimmer.standardizedAssignee(String.join("",documentPieces));
            if(text.contains("ASSIGNMENT OF ASSIGN")) isAssignorsInterest=true;
            else if(text.contains("SECURITY INTEREST")) isSecurityInterest=true;
            if(!(isSecurityInterest||isAssignorsInterest)) {
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(inPatentAssignment&&qName.equals("document-id")){
            inDocumentID=false;
        }

        if(inDocumentID&&qName.equals("doc-number")) {
            isDocNumber = false;
            String text = AssigneeTrimmer.standardizedAssignee(String.join("",documentPieces));
            currentPatents.add(text);
            documentPieces.clear();

        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        if((!shouldTerminate)&&(isDocNumber||isConveyanceText)){
            documentPieces.add(new String(ch,start,length));
        }

    }

}