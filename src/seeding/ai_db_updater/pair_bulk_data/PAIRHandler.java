package seeding.ai_db_updater.pair_bulk_data;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import org.xml.sax.SAXException;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.ui_models.attributes.computable_attributes.TermAdjustmentAttribute;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**

 */
public class PAIRHandler extends NestedHandler {
    protected static AtomicInteger cnt = new AtomicInteger(0);
    protected int batchSize = 10000;
    private Consumer<Map<String,Object>> postgresConsumer;
    public PAIRHandler(Consumer<Map<String,Object>> postgresConsumer) {
        this.postgresConsumer=postgresConsumer;
    }

    @Override
    protected void initAndAddFlagsAndEndFlags() {

        // application flags
        Flag grantNumber = Flag.customFlag("PatentNumber",Constants.GRANT_NAME,"text",(str)->!str.isEmpty()&&!str.equals("0"),null);
        Flag publicationNumber = Flag.customFlag("PublicationNumber",Constants.PUBLICATION_NAME,"text",(str)->!str.equals("0"),null);
        Flag applicationNumber = Flag.simpleFlag("ApplicationNumberText",Constants.FILING_NAME,null);
        EndFlag applicationEndFlag = new EndFlag("PatentData") {
            @Override
            public void save() {
                String filingNumber = dataMap.get(applicationNumber);
                if (filingNumber != null) {
                    filingNumber = Flag.filingDocumentHandler.apply(applicationNumber).apply(filingNumber).toString();
                    filingNumber = filingNumber.replace("/","");
                    dataMap.put(applicationNumber,filingNumber);

                    if (dataMap.containsKey(publicationNumber)) {
                        String standardizedPubNumber = dataMap.get(publicationNumber).toString();
                        if (standardizedPubNumber.startsWith("US") && standardizedPubNumber.length() == 15) {
                            standardizedPubNumber = standardizedPubNumber.substring(2, 13);
                            dataMap.put(publicationNumber, standardizedPubNumber);
                        } else {
                            dataMap.remove(publicationNumber);
                        }
                    }
                    Map<String,Object> cleanData = dataMap.entrySet().stream().collect(Collectors.toMap(e->e.getKey().dbName,e->e.getValue()));
                    postgresConsumer.accept(cleanData);
                }
                dataMap = new HashMap<>();
            }
        };

        endFlags.add(applicationEndFlag);

        // add applications children
        applicationEndFlag.addChild(grantNumber);
        applicationEndFlag.addChild(publicationNumber);
        applicationEndFlag.addChild(applicationNumber);
        applicationEndFlag.addChild(Flag.dateFlag("FilingDate",Constants.FILING_DATE,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationTypeCategory",Constants.APPLICATION_TYPE,applicationEndFlag));
        applicationEndFlag.addChild(Flag.customFlag("PartyIdentifier",Constants.CORRESPONDENT_ADDRESS_ID,"text", (str)->!str.equalsIgnoreCase("null"),applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationConfirmationNumber",Constants.APPLICATION_CONFIRMATION_NUM,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("BusinessEntityStatusCategory", Constants.ASSIGNEE_ENTITY_TYPE,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationStatusCategory",Constants.APPLICATION_STATUS,applicationEndFlag));
        applicationEndFlag.addChild(Flag.dateFlag("ApplicationStatusDate",Constants.APPLICATION_STATUS_DATE,applicationEndFlag));
        applicationEndFlag.addChild(Flag.integerFlag("AdjustmentTotalQuantity",Constants.PATENT_TERM_ADJUSTMENT,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicantFileReference",Constants.APPLICANT_FILE_REFERENCE,applicationEndFlag));

        // make sure to set end flags for these ones
        grantNumber.setEndFlag(applicationEndFlag);
        publicationNumber.setEndFlag(applicationEndFlag);
        applicationNumber.setEndFlag(applicationEndFlag);
    }

    @Override
    public CustomHandler newInstance() {
        PAIRHandler handler = new PAIRHandler(postgresConsumer);
        handler.init();
        return handler;
    }

    @Override
    public void save() {
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        super.endElement(uri,localName,qName);
        // check for end document
        if(localName.equals("PatentData")) {
            if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                System.out.println("Commit of pair data: "+cnt.get());
            }
        }
    }


}