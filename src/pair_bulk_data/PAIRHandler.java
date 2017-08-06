package pair_bulk_data;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import seeding.ai_db_updater.handlers.CustomHandler;
import tools.AssigneeTrimmer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**

 */
public class PAIRHandler extends CustomHandler{
    // functions
    private static Function<String,Boolean> validDateFunction = (str) -> {
        try {
            LocalDate.parse(str, DateTimeFormatter.ISO_DATE);
            return true;
        } catch(Exception e) {
            return false;
        }
    };

    private static Function<String,Boolean> validIntegerFunction = (str) -> {
        try {
            Integer.valueOf(str);
            return true;
        } catch(Exception e) {
            return false;
        }
    };

    protected List<String> documentPieces = new ArrayList<>();
    protected List<Flag> applicationFlags = new ArrayList<>();
    protected List<Flag> inventorFlags = new ArrayList<>();
    protected List<Flag> leafFlags = new ArrayList<>();
    protected List<Flag> applicationLeafFlags = new ArrayList<>();
    protected List<Flag> inventorLeafFlags = new ArrayList<>();
    protected Map<String,String> applicationMap = new HashMap<>();
    protected Map<String,String> inventorMap = new HashMap<>();
    protected List<Map<String,String>> inventorMaps = new ArrayList<>();
    protected static AtomicInteger cnt = new AtomicInteger(0);
    protected int batchSize = 10000;


    public PAIRHandler() {
        // invention flags
        Flag inventorFirstName = new Flag("FirstName","first_name","text");
        Flag inventorLastName = new Flag("LastName","last_name","text");
        Flag inventorCity = new Flag("CityName","city","text");
        Flag inventorCountry = new Flag("CountryCode","country","text");
        inventorLeafFlags.add(inventorFirstName);
        inventorLeafFlags.add(inventorLastName);
        inventorLeafFlags.add(inventorCity);
        inventorLeafFlags.add(inventorCountry);
        inventorLeafFlags.add(new Flag("ApplicationNumberText","application_number","text"));
        Flag inventorFlag = new Flag("Inventor",null,null);
        inventorFlag.addChild(inventorFirstName);
        inventorFlag.addChild(inventorLastName);
        inventorFlag.addChild(inventorCity);
        inventorFlag.addChild(inventorCountry);
        inventorFlags.add(inventorFlag);

        // application flags
        applicationLeafFlags.add(new Flag("ApplicationNumberText","application_number","text"));
        applicationLeafFlags.add(new Flag("FilingDate","filing_date","date",validDateFunction));
        applicationLeafFlags.add(new Flag("ApplicationTypeCategory","application_type","text"));
        applicationLeafFlags.add(new Flag("PartyIdentifier","correspondence_address_id","text", (str)->!str.equalsIgnoreCase("null")));
        applicationLeafFlags.add(new Flag("GroupArtUnitNumber","group_art_unit_number","text"));
        applicationLeafFlags.add(new Flag("ApplicationConfirmationNumber","application_confirmation_number","text"));
        applicationLeafFlags.add(new Flag("BusinessEntityStatusCategory","entity_type","text"));
        applicationLeafFlags.add(new Flag("InventionTitle","invention_title","text"));
        applicationLeafFlags.add(new Flag("ApplicationStatusCategory","application_status","text"));
        applicationLeafFlags.add(new Flag("ApplicationStatusDate","application_status_date","date",validDateFunction));
        applicationLeafFlags.add(new Flag("PatentNumber","grant_number","text"));
        applicationLeafFlags.add(new Flag("GrantDate","grant_date","date",validDateFunction));
        applicationLeafFlags.add(new Flag("PublicationNumber","publication_number","text"));
        applicationLeafFlags.add(new Flag("PublicationDate","publication_date","date",validDateFunction));
        applicationLeafFlags.add(new Flag("AdjustmentTotalQuantity","term_extension","int", validIntegerFunction));
        applicationLeafFlags.add(new Flag("ApplicantFileReference","applicant_file_reference","text"));

        applicationFlags = applicationLeafFlags; // no nested fields

        // sll leaf flags
        leafFlags.addAll(applicationLeafFlags);
        leafFlags.addAll(inventorLeafFlags);
    }

    @Override
    public CustomHandler newInstance() {
        return new PAIRHandler();
    }

    @Override
    public void reset() {
        try {
            String applicationNumber = applicationMap.get("application_number");
            if(applicationNumber!=null) {
                Database.ingestPairRecords(applicationMap, applicationLeafFlags, "pair_applications");
                inventorMaps.forEach(map->{
                    try {
                        map.put("application_number",applicationNumber);
                        Database.ingestPairRecords(map, inventorLeafFlags, "pair_application_inventors");
                    } catch(Exception e) {
                        e.printStackTrace();
                        try {
                            Database.commit();
                            Database.resetConn();
                        } catch(Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                });
            }
        } catch(Exception e) {
            e.printStackTrace();
            try {
                Database.commit();
                Database.resetConn();
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        }
        applicationMap = new HashMap<>();
        inventorMap = new HashMap<>();
        inventorMaps.clear();
        applicationFlags.forEach(flag->flag.reset());
        inventorFlags.forEach(flag->flag.reset());
        leafFlags.forEach(flag->flag.reset());
    }

    @Override
    public void save() {
        try {
            Database.commit();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        applicationFlags.forEach(flag->{
            startHelper(Arrays.asList(flag),localName);
        });
        inventorFlags.forEach(flag->{
            startHelper(Arrays.asList(flag),localName);
        });
    }

    private void startHelper(List<Flag> list, String localName) {
        if(!list.isEmpty()) {
            list.forEach(item->{
                item.setTrueIfEqual(localName);
                if(item.get()) {
                    startHelper(item.children, localName);
                }
            });
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        AtomicBoolean shouldClear = new AtomicBoolean(false);
        applicationFlags.forEach(flag->{
            endHelper(Arrays.asList(flag),localName,false, shouldClear);
        });
        inventorFlags.forEach(flag->{
            endHelper(Arrays.asList(flag),localName,true, shouldClear);
        });

        // check for end of inventor
        if(localName.equals("Inventor")) {
            inventorMaps.add(inventorMap);
            inventorMap = new HashMap<>();
        }

        // check for end document
        if(localName.equals("PatentData")) {
            reset();
            if(cnt.getAndIncrement() % batchSize == batchSize-1) {
                System.out.println("Commit: "+cnt.get());
                save();
            }
        }

        // check if we need to clear documents
        if(shouldClear.get()) {
            documentPieces.clear();
        }
    }


    private void endHelper(List<Flag> list, String localName, boolean inventor, AtomicBoolean shouldClear) {
        if(!list.isEmpty()) {
            list.forEach(item->{
                endHelper(item.children,localName, inventor,shouldClear);
                if(item.localName.equals(localName)) {
                    item.reset();
                    if(item.isLeaf()) {
                        final String text = String.join("", documentPieces).trim();
                        shouldClear.set(true);
                        if(item.validValue(text)) {
                            if (inventor) {
                                inventorMap.put(item.dbName, text);
                            } else {
                                applicationMap.put(item.dbName, text);
                            }
                        }
                    }
                }
            });
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{
        if(leafFlags.stream().anyMatch(flag->flag.get())){
            documentPieces.add(new String(ch,start,length));
        }
    }


   public class Flag {
        public String localName;
        public String dbName;
        AtomicBoolean flag;
        public List<Flag> children;
        public String type;
       Function<String,Boolean> validValueFunction;
        Flag(String localName, String dbName, String type, Function<String,Boolean> validValueFunction) {
            this.dbName=dbName;
            this.validValueFunction=validValueFunction;
            this.type=type;
            this.localName=localName;
            this.flag = new AtomicBoolean(false);
            this.children = new ArrayList<>();
        }

       Flag(String localName, String dbName, String type) {
            this(localName,dbName,type,(str)->true);
       }

        public boolean validValue(String text) {
            return validValueFunction.apply(text);
        }

        boolean get() {
            return flag.get();
        }

        void addChild(Flag child) {
            children.add(child);
        }

        boolean isLeaf() {
            return children.isEmpty();
        }

        void setTrueIfEqual(String otherName) {
            if(localName.equals(otherName)) {
                flag.set(true);
            }
        }

        void reset() {
            flag.set(false);
        }
    }
}