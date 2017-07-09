package ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import ai_db_updater.database.Database;
import ai_db_updater.tools.PhrasePreprocessor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import tools.AssigneeTrimmer;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**

 */
public class SAXFullTextHandler extends CustomHandler{
    private boolean inPublicationReference=false;
    private boolean isDocNumber=false;
    private boolean isWithinDocument=false;
    private boolean shouldTerminate = false;
    private String pubDocNumber;
    private List<String> fullDocuments=new ArrayList<>();
    private List<String> documentPieces=new ArrayList<>();
    private static AtomicInteger cnt = new AtomicInteger(0);
    private static final int wordLimit = 500;
    protected PortfolioList.Type type;

    public SAXFullTextHandler(PortfolioList.Type type) {
        this.type=type;
    }

    private void update() {
        if (pubDocNumber != null && !fullDocuments.isEmpty() && !shouldTerminate) {
            try {
                Database.ingestTextRecords(pubDocNumber, type, fullDocuments);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void reset() {
        update();
        inPublicationReference=false;
        isDocNumber=false;
        shouldTerminate = false;
        isWithinDocument=false;
        fullDocuments.clear();
        documentPieces.clear();
        pubDocNumber=null;
        if (cnt.getAndIncrement()%10000==0)
            try {
                Database.commit();
                System.out.println("Committed: "+cnt.get());
            } catch(Exception e) {
                e.printStackTrace();
            }
    }

    @Override
    public void save() {
        // do nothing
        try {
            Database.commit();
            Database.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CustomHandler newInstance() {
        return new SAXFullTextHandler(type);
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        if(shouldTerminate) return;

        //System.out.println("Start Element :" + qName);

        if(qName.equals("publication-reference")){
            inPublicationReference=true;
        }

        if(qName.equals("claim")||qName.equals("abstract")||qName.equals("description")){
            isWithinDocument=true;
        }

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=true;
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        if(shouldTerminate) return;

        if(qName.equals("doc-number")&&inPublicationReference){
            isDocNumber=false;
            pubDocNumber=String.join("",documentPieces).replaceAll("[^A-Z0-9]","");
            if(pubDocNumber.startsWith("0"))pubDocNumber = pubDocNumber.substring(1,pubDocNumber.length());
            if(pubDocNumber.isEmpty()||Arrays.asList('D','R','H','P').contains(pubDocNumber.charAt(0))) {
                pubDocNumber=null;
                shouldTerminate = true;
            }
            documentPieces.clear();
        }

        if(qName.equals("publication-reference")){
            inPublicationReference=false;
        }

        if(qName.equals("claim")||qName.equals("abstract")||qName.equals("description")){
            isWithinDocument = false;
            String text = String.join(" ",documentPieces);
            if (text.length() > 5) {
                fullDocuments.add(text);
            }
            documentPieces.clear();
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{
        if((!shouldTerminate)&&(isWithinDocument||isDocNumber)){
            if(isWithinDocument) {
                length = Math.min(length, wordLimit * 20); // avoid overflow
                documentPieces.add(extractTokens(new String(ch, start, length)));
            } else {
                documentPieces.add(new String(ch, start, length));
            }
        }

    }

    private static String extractTokens(String toExtract) {
        return toExtract.toLowerCase().replaceAll("[^a-z.]","");
    }

}