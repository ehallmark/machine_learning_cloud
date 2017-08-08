package pair_bulk_data;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
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

/**

 */
public class PAIRHandler extends NestedHandler {

    protected static AtomicInteger cnt = new AtomicInteger(0);
    protected int batchSize = 10000;

    @Override
    protected void initAndAddFlagsAndEndFlags() {

        // application flags
        Flag assigneeFlag = Flag.fakeFlag("assignee");
        Flag grantNumber = Flag.customFlag("PatentNumber","grant_number","text",(str)->!str.equals("0"),null);
        Flag publicationNumber = Flag.customFlag("PublicationNumber","publication_number","text",(str)->!str.equals("0"),null);
        Flag applicationNumber = Flag.simpleFlag("ApplicationNumberText","application_number",null);
        EndFlag applicationEndFlag = new EndFlag("PatentData") {
            @Override
            public void save() {
                Object appNumber = dataMap.get(applicationNumber);
                if (appNumber != null) {
                    Set<String> assignees = new HashSet<>();
                    if (dataMap.containsKey(grantNumber)) {
                        assignees.addAll(Database.assigneesFor(dataMap.get(grantNumber).toString()));
                    }
                    if (dataMap.containsKey(publicationNumber)) {
                        String standardizedPubNumber = dataMap.get(publicationNumber).toString();
                        if (standardizedPubNumber.startsWith("US") && standardizedPubNumber.length() == 15) {
                            standardizedPubNumber = standardizedPubNumber.substring(2, 13);
                            assignees.addAll(Database.assigneesFor(standardizedPubNumber));
                            dataMap.put(publicationNumber, standardizedPubNumber);
                        } else {
                            dataMap.remove(publicationNumber);
                        }
                    }
                    if (!assignees.isEmpty()) {
                        dataMap.put(assigneeFlag, assignees.stream().findAny().get());
                    }
                    try {
                        Database.ingestPairRecords(dataMap, "pair_applications");
                    } catch (Exception e) {
                        e.printStackTrace();
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
        applicationEndFlag.addChild(Flag.dateFlag("FilingDate","filing_date",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationTypeCategory","application_type",applicationEndFlag));
        applicationEndFlag.addChild(Flag.customFlag("PartyIdentifier","correspondence_address_id","text", (str)->!str.equalsIgnoreCase("null"),applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("GroupArtUnitNumber","group_art_unit_number",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationConfirmationNumber","application_confirmation_number",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("BusinessEntityStatusCategory","entity_type",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("InventionTitle","invention_title",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicationStatusCategory","application_status",applicationEndFlag));
        applicationEndFlag.addChild(Flag.dateFlag("ApplicationStatusDate","application_status_date",applicationEndFlag));
        applicationEndFlag.addChild(Flag.dateFlag("GrantDate","grant_date",applicationEndFlag));
        applicationEndFlag.addChild(Flag.dateFlag("PublicationDate","publication_date",applicationEndFlag));
        applicationEndFlag.addChild(Flag.integerFlag("AdjustmentTotalQuantity","term_extension",applicationEndFlag));
        applicationEndFlag.addChild(Flag.simpleFlag("ApplicantFileReference","applicant_file_reference",applicationEndFlag));

        // make sure to set end flags for these ones
        grantNumber.setEndFlag(applicationEndFlag);
        publicationNumber.setEndFlag(applicationEndFlag);
        applicationNumber.setEndFlag(applicationEndFlag);

        // invention flags
        EndFlag inventorEndFlag = new EndFlag("Inventor") {
            @Override
            public void save() {
                String appNum = applicationEndFlag.getDataMap().get(applicationNumber);
                if(appNum!=null) {
                    dataMap.put(applicationNumber, appNum);
                    try {
                        Database.ingestPairRecords(dataMap, "pair_application_inventors");
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                dataMap = new HashMap<>();
            }
        };

        Flag inventorFirstName = Flag.simpleFlag("FirstName","first_name",inventorEndFlag);
        Flag inventorLastName = Flag.simpleFlag("LastName","last_name",inventorEndFlag);
        Flag inventorCity = Flag.simpleFlag("CityName","city",inventorEndFlag);
        Flag inventorCountry = Flag.simpleFlag("CountryCode","country",inventorEndFlag);
        inventorEndFlag.addChild(inventorFirstName);
        inventorEndFlag.addChild(inventorLastName);
        inventorEndFlag.addChild(inventorCity);
        inventorEndFlag.addChild(inventorCountry);
        endFlags.add(inventorEndFlag);
    }

    @Override
    public CustomHandler newInstance() {
        PAIRHandler handler = new PAIRHandler();
        handler.init();
        return handler;
    }

    @Override
    public void save() {
        try {
            Database.commit();
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