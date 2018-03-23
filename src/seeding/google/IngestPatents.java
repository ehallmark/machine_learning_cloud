package seeding.google;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static seeding.google.IngestJsonHelper.ingestJsonDump;

public class IngestPatents {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "patents";

    public static final String FULL_PUBLICATION_NUMBER = "pub_num_full";
    public static final String PUBLICATION_NUMBER_WITH_COUNTRY = "pub_num_country";
    public static final String PUBLICATION_NUMBER = "pub_num";
    public static final String PUBLICATION_NUMBER_GOOGLE = "publication_number";
    public static final String FULL_APPLICATION_NUMBER = "app_num_full";
    public static final String APPLICATION_NUMBER_WITH_COUNTRY = "app_num_country";
    public static final String APPLICATION_NUMBER = "app_num";
    public static final String APPLICATION_NUMBER_GOOGLE = "application_number";

    public static void main(String[] args) {
        final List<Function<Map<String,Object>,Void>> attributeFunctions = Arrays.asList(
                map -> {
                    // handle publication numbers
                    String publicationNumber = (String)map.get(PUBLICATION_NUMBER_GOOGLE);
                    if(publicationNumber!=null) {
                        String[] parts = publicationNumber.split("-");
                        if(parts.length==3) {
                            String fullNumber = String.join("",parts);
                            String countryAndNumber = parts[0]+parts[1];
                            String number = parts[1];
                            map.put(FULL_PUBLICATION_NUMBER,fullNumber);
                            map.put(PUBLICATION_NUMBER_WITH_COUNTRY,countryAndNumber);
                            map.put(PUBLICATION_NUMBER,number);
                        } else {

                            System.out.println("Error with publication number: "+publicationNumber);
                        }
                    }
                    return null;
                }, map -> {
                    // handle filing numbers
                    String applicationNumber = (String)map.get(APPLICATION_NUMBER_GOOGLE);
                    if(applicationNumber!=null) {
                        String[] parts = applicationNumber.split("-");
                        String fullNumber = String.join("",parts);
                        String countryAndNumber = parts[0]+parts[1];
                        String number = parts[1];
                        map.put(FULL_APPLICATION_NUMBER,fullNumber);
                        map.put(APPLICATION_NUMBER_WITH_COUNTRY,countryAndNumber);
                        map.put(APPLICATION_NUMBER,number);
                    }
                    return null;
                }
        );


        final String idField = "publication_number";
        final File dataDir = new File("/usb2/data/google-big-query/patents/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);
        final LocalDate twentyFiveYearsAgo = LocalDate.now().minusYears(25);
        final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
            String filingDate = (String)doc.get("filing_date");
            if(filingDate==null||filingDate.length()!=8) return false;
            boolean valid = LocalDate.parse(filingDate, DateTimeFormatter.BASIC_ISO_DATE).isAfter(twentyFiveYearsAgo);
            if(valid) {
                // add other attributes
                attributeFunctions.forEach(function->{
                    function.apply(doc);
                });
            }
            return valid;
        };

        ingestJsonDump(idField,dataDir,collection,true,filterDocumentFunction);
    }
}
