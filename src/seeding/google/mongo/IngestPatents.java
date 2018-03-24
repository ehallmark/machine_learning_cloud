package seeding.google.mongo;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;
import org.bson.Document;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static seeding.google.mongo.IngestJsonHelper.ingestJsonDump;
import static seeding.google.attributes.Constants.*;

public class IngestPatents {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "patents";


    public static void main(String[] args) {
        final List<Function<Document,Void>> attributeFunctions = Arrays.asList(
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
                        if(parts.length==3) {
                            String fullNumber = String.join("", parts);
                            String countryAndNumber = parts[0] + parts[1];
                            String number = parts[1];
                            String formatted = (String)map.get(APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
                            if(formatted!=null) {
                                if(formatted.startsWith(parts[0])&&formatted.length()>parts[0].length()) {
                                    formatted = formatted.substring(parts[0].length(),formatted.length());
                                }
                                map.put(APPLICATION_NUMBER_FORMATTED,formatted);
                            }
                            map.put(FULL_APPLICATION_NUMBER, fullNumber);
                            map.put(APPLICATION_NUMBER_WITH_COUNTRY, countryAndNumber);
                            map.put(APPLICATION_NUMBER, number);
                        } else {
                            System.out.println("Error with publication number: "+applicationNumber);
                        }
                    }
                    return null;
                }
        );


        final String idField = PUBLICATION_NUMBER_GOOGLE;
        final File dataDir = new File("/usb2/data/google-big-query/patents/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);
        final LocalDate twentyFiveYearsAgo = LocalDate.now().minusYears(25);
        final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
            String filingDate = (String)doc.get(FILING_DATE);
            if(filingDate==null||filingDate.length()!=8) return false;
            return LocalDate.parse(filingDate, DateTimeFormatter.BASIC_ISO_DATE).isAfter(twentyFiveYearsAgo);
        };

        ingestJsonDump(idField,dataDir,collection,true,filterDocumentFunction,attributeFunctions);
    }
}
