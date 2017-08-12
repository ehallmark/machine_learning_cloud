package pair_bulk_data;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import tools.AssigneeTrimmer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**

 */
public class PAIRHandler extends NestedHandler {

    protected static AtomicInteger cnt = new AtomicInteger(0);
    protected int batchSize = 10000;
    protected boolean updatePostgres;
    protected boolean updateElasticSearch;
    protected boolean loadExternalAssignees;
    public PAIRHandler(boolean loadExternalAssignees, boolean updatePostgres, boolean updateElasticSearch) {
        this.updatePostgres=updatePostgres;
        this.loadExternalAssignees=loadExternalAssignees;
        this.updateElasticSearch=updateElasticSearch;
    }

    @Override
    protected void initAndAddFlagsAndEndFlags() {

        // application flags
        Flag assigneeFlag = Flag.fakeFlag("assignee");
        Flag grantNumber = Flag.customFlag("PatentNumber",Constants.GRANT_NAME,"text",(str)->!str.equals("0"),null);
        Flag publicationNumber = Flag.customFlag("PublicationNumber",Constants.PUBLICATION_NAME,"text",(str)->!str.equals("0"),null);
        Flag applicationNumber = Flag.simpleFlag("ApplicationNumberText",Constants.FILING_NAME,null);
        EndFlag applicationEndFlag = new EndFlag("PatentData") {
            @Override
            public void save() {
                Object appNumber = dataMap.get(applicationNumber);
                if (appNumber != null) {
                    Set<String> assignees = new HashSet<>();
                    if (loadExternalAssignees && dataMap.containsKey(grantNumber)) {
                        assignees.addAll(Database.assigneesFor(dataMap.get(grantNumber).toString()));
                    }
                    if (dataMap.containsKey(publicationNumber)) {
                        String standardizedPubNumber = dataMap.get(publicationNumber).toString();
                        if (standardizedPubNumber.startsWith("US") && standardizedPubNumber.length() == 15) {
                            standardizedPubNumber = standardizedPubNumber.substring(2, 13);
                            if (loadExternalAssignees) assignees.addAll(Database.assigneesFor(standardizedPubNumber));
                            dataMap.put(publicationNumber, standardizedPubNumber);
                        } else {
                            dataMap.remove(publicationNumber);
                        }
                    }
                    if (loadExternalAssignees && !assignees.isEmpty()) {
                        dataMap.put(assigneeFlag, assignees.stream().findAny().get());
                    }
                    if(updatePostgres) {
                        try {
                            Database.ingestPairRecords(dataMap, "pair_applications");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if(updateElasticSearch) {
                        String appNum = dataMap.get(publicationNumber);
                        String grantNum = dataMap.get(grantNumber);
                        if(appNum!=null || grantNum != null) {
                            Map<String,Object> cleanData = dataMap.entrySet().stream().collect(Collectors.toMap(e->e.getKey().dbName,e->{
                                if(e.getKey().equals(Constants.PATENT_TERM_ADJUSTMENT)) return Integer.valueOf(e.getValue());
                                else return e.getValue();
                            }));
                            if(appNum!=null) {
                                DataIngester.ingestBulk(appNum, cleanData, false);
                            }
                            if(grantNum!=null) {
                                DataIngester.ingestBulk(grantNum,cleanData,false);
                            }
                        }

                    }
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
        if(updatePostgres) applicationEndFlag.addChild(Flag.simpleFlag("GroupArtUnitNumber","groupArtUnitNumber",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationConfirmationNumber",Constants.APPLICATION_CONFIRMATION_NUM,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("BusinessEntityStatusCategory", Constants.ASSIGNEE_ENTITY_TYPE,applicationEndFlag));
        if(updatePostgres) applicationEndFlag.addChild(Flag.simpleFlag("InventionTitle",Constants.INVENTION_TITLE,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationStatusCategory",Constants.APPLICATION_STATUS,applicationEndFlag));
        applicationEndFlag.addChild(Flag.dateFlag("ApplicationStatusDate",Constants.APPLICATION_STATUS_DATE,applicationEndFlag));
        if(updatePostgres) applicationEndFlag.addChild(Flag.dateFlag("GrantDate","grantDate",applicationEndFlag));
        if(updatePostgres) applicationEndFlag.addChild(Flag.dateFlag("PublicationDate","publicationDate",applicationEndFlag));
        applicationEndFlag.addChild(Flag.integerFlag("AdjustmentTotalQuantity",Constants.PATENT_TERM_ADJUSTMENT,applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicantFileReference",Constants.APPLICANT_FILE_REFERENCE,applicationEndFlag));

        // make sure to set end flags for these ones
        grantNumber.setEndFlag(applicationEndFlag);
        publicationNumber.setEndFlag(applicationEndFlag);
        applicationNumber.setEndFlag(applicationEndFlag);

        if(updatePostgres) {
            // invention flags
            EndFlag inventorEndFlag = new EndFlag("Inventor") {
                @Override
                public void save() {
                    String appNum = applicationEndFlag.getDataMap().get(applicationNumber);
                    if (appNum != null) {
                        dataMap.put(applicationNumber, appNum);
                        try {
                            Database.ingestPairRecords(dataMap, "pair_application_inventors");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    dataMap = new HashMap<>();
                }
            };

            Flag inventorFirstName = Flag.simpleFlag("FirstName", Constants.FIRST_NAME, inventorEndFlag);
            Flag inventorLastName = Flag.simpleFlag("LastName", Constants.LAST_NAME, inventorEndFlag);
            Flag inventorCity = Flag.simpleFlag("CityName", Constants.CITY, inventorEndFlag);
            Flag inventorCountry = Flag.simpleFlag("CountryCode", Constants.COUNTRY, inventorEndFlag);
            inventorEndFlag.addChild(inventorFirstName);
            inventorEndFlag.addChild(inventorLastName);
            inventorEndFlag.addChild(inventorCity);
            inventorEndFlag.addChild(inventorCountry);
            endFlags.add(inventorEndFlag);
        }
    }

    @Override
    public CustomHandler newInstance() {
        PAIRHandler handler = new PAIRHandler(loadExternalAssignees,updatePostgres,updateElasticSearch);
        handler.init();
        return handler;
    }

    @Override
    public void save() {
        try {
            if(updatePostgres) Database.commit();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        super.endElement(uri,localName,qName);
        // check for end document
        if(localName.equals("PatentData")) {
            if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                System.out.println("Commit: "+cnt.get());
                save();
            }
        }
    }


}