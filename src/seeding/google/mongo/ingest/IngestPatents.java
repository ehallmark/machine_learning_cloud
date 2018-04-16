package seeding.google.mongo.ingest;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;
import org.bson.Document;
import seeding.google.postgres.SeedingConstants;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static seeding.google.mongo.ingest.IngestJsonHelper.ingestJsonDump;

public class IngestPatents {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "patents";


    public static void main(String[] args) {
        final List<Function<Document,Void>> attributeFunctions = Arrays.asList(
                map -> {
                    // handle publication numbers
                    String publicationNumber = (String)map.get(SeedingConstants.PUBLICATION_NUMBER_GOOGLE);
                    if(publicationNumber!=null) {
                        String[] parts = publicationNumber.split("-");
                        if(parts.length==3) {
                            String fullNumber = String.join("",parts);
                            String countryAndNumber = parts[0]+parts[1];
                            String number = parts[1];
                            map.put(SeedingConstants.FULL_PUBLICATION_NUMBER,fullNumber);
                            map.put(SeedingConstants.PUBLICATION_NUMBER_WITH_COUNTRY,countryAndNumber);
                            map.put(SeedingConstants.PUBLICATION_NUMBER,number);
                        } else {

                            System.out.println("Error with publication number: "+publicationNumber);
                        }
                    }
                    return null;
                }, map -> {
                    // handle filing numbers
                    String applicationNumber = (String)map.get(SeedingConstants.APPLICATION_NUMBER_GOOGLE);
                    if(applicationNumber!=null) {
                        String[] parts = applicationNumber.split("-");
                        if(parts.length==3) {
                            String fullNumber = String.join("", parts);
                            String countryAndNumber = parts[0] + parts[1];
                            String number = parts[1];
                            String formatted = (String)map.get(SeedingConstants.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
                            if(formatted!=null) {
                                if(formatted.startsWith(parts[0])&&formatted.length()>parts[0].length()) {
                                    formatted = formatted.substring(parts[0].length(),formatted.length());
                                }
                                map.put(SeedingConstants.APPLICATION_NUMBER_FORMATTED,formatted);
                            }
                            map.put(SeedingConstants.FULL_APPLICATION_NUMBER, fullNumber);
                            map.put(SeedingConstants.APPLICATION_NUMBER_WITH_COUNTRY, countryAndNumber);
                            map.put(SeedingConstants.APPLICATION_NUMBER, number);
                        } else {
                            System.out.println("Error with publication number: "+applicationNumber);
                        }
                    }
                    return null;
                }
        );


        final String idField = SeedingConstants.PUBLICATION_NUMBER_GOOGLE;
        final File dataDir = new File("/usb2/data/google-big-query/patents/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);
        final LocalDate twentyFiveYearsAgo = LocalDate.now().minusYears(25);
        final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
            String filingDate = (String)doc.get(SeedingConstants.FILING_DATE);
            if(filingDate==null||filingDate.length()!=10) return false;
            return LocalDate.parse(filingDate, DateTimeFormatter.ISO_DATE).isAfter(twentyFiveYearsAgo);
        };

        ingestJsonDump(idField,dataDir,collection,true,filterDocumentFunction,attributeFunctions);
    }
}
