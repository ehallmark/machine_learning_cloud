package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import seeding.ai_db_updater.tools.PhrasePreprocessor;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**

 */
public class AppClaimDataSAXHandler extends ClaimDataSAXHandler{
    private static PhrasePreprocessor phrasePreprocessor = new PhrasePreprocessor();
    public static final File appToIndependentClaimLengthFile = new File("app_to_independent_claim_length_map.jobj");
    public static final File appToIndependentClaimRatioFile = new File("app_to_independent_claim_ratio_map.jobj");
    public static final File appToMeansPresentRatioFile = new File("app_to_means_present_ratio_map.jobj");

    protected AppClaimDataSAXHandler(Map<String,Integer> patentToIndependentClaimLengthMap, Map<String,Double> patentToIndependentClaimRatioMap, Map<String,Double> patentToMeansPresentRatioMap) {
        super(patentToIndependentClaimLengthMap,patentToIndependentClaimRatioMap,patentToMeansPresentRatioMap);
    }

    public AppClaimDataSAXHandler() {
        super();
    }

    @Override
    public void save() {
        System.out.println("Saving results...");
        // save maps
        Database.saveObject(patentToIndependentClaimLengthMap,appToIndependentClaimLengthFile);
        Database.saveObject(patentToIndependentClaimRatioMap,appToIndependentClaimRatioFile);
        Database.saveObject(patentToMeansPresentRatioMap,appToMeansPresentRatioFile);
    }

    @Override
    public CustomHandler newInstance() {
        return new AppClaimDataSAXHandler(patentToIndependentClaimLengthMap,patentToIndependentClaimRatioMap,patentToMeansPresentRatioMap);
    }

    public String getPatentNumber() {
        return pubDocNumber;
    }

    public double getIndependentClaimRatio() {
        return new Double(independentClaimCount)/Math.max(1,totalClaimCount);
    }

    public double getMeansPresentCountRatio() { return new Double(meansPresentCount)/Math.max(1,independentClaimCount); }

    public int getIndependentClaimLength() { return independentClaimLength; }

    protected void update() {
        if (pubDocNumber != null) {
            if(independentClaimLength>0) {
                patentToIndependentClaimLengthMap.put(pubDocNumber, independentClaimLength);
            }
            double iClaimRatio = getIndependentClaimRatio();
            if(iClaimRatio>0) {
                patentToIndependentClaimRatioMap.put(pubDocNumber, iClaimRatio);
            }
            double meansPresentRatio = getMeansPresentCountRatio();
            System.out.println(meansPresentRatio);
            patentToMeansPresentRatioMap.put(pubDocNumber, meansPresentRatio);
        }

    }

    public void reset() {
        update();
        inPublicationReference=false;
        isClaimText=false;
        claimLevel=0;
        isDocNumber=false;
        shouldTerminate = false;
        independentClaimLength=0;
        independentClaimCount=0;
        totalClaimCount=0;
        meansPresentCount=0;
        documentPieces.clear();
        pubDocNumber=null;
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{

        //System.out.println("Start Element :" + qName);

        if(qName.equals("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }

        if(qName.equals("claim-text")) {
            isClaimText=true;
            claimLevel++;
        }

    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{

        //System.out.println("End Element :" + qName);

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());

            if(pubDocNumber.replaceAll("[^0-9]","").length()!=pubDocNumber.length()) {
                pubDocNumber=null;
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(qName.equals("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equals("claim-text")) {
            isClaimText=false;
            if(claimLevel==1) {
                List<String> tokens = extractTokens(String.join(" ", documentPieces));
                independentClaimLength += tokens.size();
                independentClaimCount++;
                if(tokens.contains("means")) {
                    meansPresentCount++;
                }
            }
            claimLevel=Math.max(claimLevel-1,0);
            documentPieces.clear();
            totalClaimCount++;
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{

        // Example
        // if (bfname) {
        //    System.out.println("First Name : " + new String(ch, start, length));
        //    bfname = false;
        // }

        if((!shouldTerminate)&&((isClaimText&&(claimLevel==1))||isDocNumber)){
            documentPieces.add(new String(ch,start,length));
        }

    }

    private static List<String> extractTokens(String toExtract,boolean phrases) {
        String data = toExtract.toLowerCase().replaceAll("[^a-z ]"," ");
        return Arrays.stream((phrases?phrasePreprocessor.preProcess(data):data).split("\\s+"))
                .filter(t->t!=null&&t.length()>0)
                .collect(Collectors.toList());
    }

    private static List<String> extractTokens(String toExtract) {
        return extractTokens(toExtract,false);
    }
}