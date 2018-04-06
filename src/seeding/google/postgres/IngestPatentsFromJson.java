package seeding.google.postgres;

import com.mongodb.client.model.WriteModel;
import elasticsearch.IngestMongoIntoElasticSearch;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestJsonHelper;
import seeding.google.mongo.ingest.IngestPatents;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static seeding.google.attributes.Constants.*;
import static seeding.google.attributes.Constants.PUBLICATION_NUMBER_GOOGLE;

public class IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
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

        final File dataDir = new File("/usb2/data/google-big-query/patents/");

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.PUBLICATION_NUMBER,
                Constants.FULL_APPLICATION_NUMBER,
                Constants.APPLICATION_NUMBER,
                Constants.APPLICATION_NUMBER_FORMATTED,
                Constants.FILING_DATE,
                Constants.PUBLICATION_DATE,
                Constants.PRIORITY_DATE,
                Constants.COUNTRY_CODE,
                Constants.KIND_CODE,
                Constants.APPLICATION_KIND,
                Constants.FAMILY_ID,
                Constants.ENTITY_STATUS
        };

        Connection conn = Database.getConn();

        String valueStr = "(?,?,?,?,?,?::date,?::date,?::date,?,?,?,?,?)";
        String conflictStr = "(?,?,?,?,?::date,?::date,?::date,?,?,?,?,?)";
        final String sql = "insert into big_query_patents (publication_number_full,publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,original_entity_type) values "+valueStr+" on conflict (publication_number_full) do update set (publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,original_entity_type) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);


        Consumer<Document> consumer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
                    Object val = doc.get(fields[i]);
                    if(i==7) { // priority date
                        if(val==null||val.equals("0")) {
                            // default to filing date
                            val = data.get(5);
                        }
                    }
                    data.add(val);
                }
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        final LocalDate twentyFiveYearsAgo = LocalDate.now().minusYears(25);
        final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
            String filingDate = (String)doc.get(FILING_DATE);
            if(filingDate==null||filingDate.length()!=10) return false;
            return LocalDate.parse(filingDate, DateTimeFormatter.ISO_DATE).isAfter(twentyFiveYearsAgo);
        };

        Stream.of(dataDir.listFiles()).forEach(file-> {
            try(InputStream stream = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                IngestJsonHelper.streamJsonFile(stream,attributeFunctions).filter(map->filterDocumentFunction.apply(map)).forEach(map->{
                    consumer.accept(new Document(map));
                });

            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });

        queryStream.close();
        conn.close();
    }

}
