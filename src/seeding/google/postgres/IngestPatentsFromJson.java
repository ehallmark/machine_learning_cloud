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
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static seeding.google.attributes.Constants.*;
import static seeding.google.attributes.Constants.PUBLICATION_NUMBER_GOOGLE;

public class IngestPatentsFromJson {

    private static final LocalDate twentyFiveYearsAgo = LocalDate.of(2018,4,7).minusYears(25);
    protected static final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
        String filingDate = (String)doc.get(FILING_DATE);
        if(filingDate==null||filingDate.length()!=10) return false;
        return LocalDate.parse(filingDate, DateTimeFormatter.ISO_DATE).isAfter(twentyFiveYearsAgo);
    };

    protected static final List<Function<Document,Void>> attributeFunctions = Arrays.asList(
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

    public static void main(String[] args) throws SQLException {


        final File dataDir = new File("/home/ehallmark/google-big-query/google-big-query/patents/");

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
                Constants.ENTITY_STATUS,
                Constants.TITLE_LOCALIZED+"."+Constants.TEXT,
                Constants.TITLE_LOCALIZED+"."+Constants.LANGUAGE,
                Constants.ABSTRACT_LOCALIZED+"."+Constants.TEXT,
                Constants.ABSTRACT_LOCALIZED+"."+Constants.LANGUAGE,
                Constants.CLAIMS_LOCALIZED+"."+Constants.TEXT,
                Constants.CLAIMS_LOCALIZED+"."+Constants.LANGUAGE,
                Constants.DESCRIPTION_LOCALIZED+"."+Constants.TEXT,
                Constants.DESCRIPTION_LOCALIZED+"."+Constants.LANGUAGE,
                Constants.INVENTOR,
                Constants.ASSIGNEE,
                Constants.INVENTOR_HARMONIZED+"."+Constants.NAME,
                Constants.INVENTOR_HARMONIZED+"."+Constants.COUNTRY_CODE,
                Constants.ASSIGNEE_HARMONIZED+"."+Constants.NAME,
                Constants.ASSIGNEE_HARMONIZED+"."+Constants.COUNTRY_CODE,
                Constants.PRIORITY_CLAIM+"."+Constants.FULL_PUBLICATION_NUMBER,
                Constants.PRIORITY_CLAIM+"."+Constants.FULL_APPLICATION_NUMBER,
                Constants.PRIORITY_CLAIM+"."+Constants.FILING_DATE,
                Constants.CPC+"."+ Constants.CODE,
                Constants.CPC+"."+ Constants.INVENTIVE,
                Constants.CITATION+"."+Constants.FULL_PUBLICATION_NUMBER,
                Constants.CITATION+"."+Constants.FULL_APPLICATION_NUMBER,
                Constants.CITATION+"."+Constants.NPL_TEXT,
                Constants.CITATION+"."+Constants.TYPE,
                Constants.CITATION+"."+Constants.CATEGORY,
                Constants.CITATION+"."+Constants.FILING_DATE
        };

        Set<String> arrayFields = new HashSet<>();
        arrayFields.add(Constants.TITLE_LOCALIZED);
        arrayFields.add(Constants.DESCRIPTION_LOCALIZED);
        arrayFields.add(Constants.CLAIMS_LOCALIZED);
        arrayFields.add(Constants.INVENTOR);
        arrayFields.add(Constants.ASSIGNEE);
        arrayFields.add(Constants.ASSIGNEE_HARMONIZED);
        arrayFields.add(Constants.INVENTOR_HARMONIZED);
        arrayFields.add(Constants.PRIORITY_CLAIM);
        arrayFields.add(Constants.CPC);
        arrayFields.add(Constants.CITATION);

        Set<String> booleanFields = new HashSet<>();
        booleanFields.add(Constants.CPC+"."+Constants.INVENTIVE);
        Connection conn = Database.getConn();

        int numFields = fields.length;
        String valueStr = "("+String.join(",",IntStream.range(0,numFields).mapToObj(i->{
            String field = fields[i];
            String parentField;
            String childField;
            if(field.contains(".")) {
                parentField = field.substring(0,field.indexOf("."));
                childField = field.substring(field.indexOf(".")+1,field.length());
            } else {
                parentField = field;
                childField = field;
            }
            boolean isDate = childField.equals("date")||childField.endsWith("_date")||childField.endsWith("Date");
            String ret = "?";
            if(arrayFields.contains(parentField)) {
                // is array field
                if(isDate) {
                    ret += "::date[]";
                } else if(booleanFields.contains(field)) {
                    ret += "::boolean[]";
                }
            } else {
                // not an array field
                if(isDate) {
                    ret+= "::date";
                } else if(booleanFields.contains(field)) {
                    ret += "::boolean";
                }
            }
            return ret;
        }).collect(Collectors.toList()))+")";

        final String sql = "insert into patents_global (publication_number_full,publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,original_entity_type,invention_title,invention_title_lang,abstract,abstract_lang,claims,claims_lang,description,description_lang,inventor,assignee,inventor_harmonized,inventor_harmonized_cc,assignee_harmonized,assignee_harmonized_cc,pc_publication_number_full,pc_application_number_full,pc_filing_date,code,inventive,cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date) values "+valueStr+" on conflict do nothing";

        DefaultApplier applier = new DefaultApplier(false, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        Consumer<Document> consumer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
                    String field = fields[i];
                    Object val;
                    if(field.contains(".")) {
                        boolean isDate = field.equals("date")||field.endsWith("_date")||field.endsWith("Date");
                        List<Map<String,Object>> fieldData = (List<Map<String,Object>>)doc.get(field.substring(0,field.indexOf(".")));
                        if(fieldData!=null&&fieldData.size()>0) {
                            Object[] objs = new Object[fieldData.size()];
                            for(int j = 0; j < fieldData.size(); j++) {
                                Map<String,Object> obj = fieldData.get(j);
                                objs[j]=obj.get(field.substring(field.indexOf(".")+1,field.length()));
                                if(objs[j]!=null&&objs[j]instanceof String) objs[j] = ((String)objs[j]).trim();
                                if(objs[j]!=null&&objs[j].toString().isEmpty()) objs[j]=null;
                                if(isDate) {
                                    if(objs[j]!=null&&objs[j].equals("0")) objs[j] = null;
                                    if(objs[j]!=null) {
                                        String date = (String)objs[j];
                                        if(date.endsWith("00")) {
                                            date = date.substring(0,date.length()-1)+"1";
                                        }
                                        if(!date.contains("-")) {
                                            date = LocalDate.parse(date,DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ISO_DATE);
                                        }
                                        objs[j]=date;
                                    }
                                }
                            }
                            val = objs;
                        } else {
                            val = null;
                        }
                    } else {
                        val = doc.get(fields[i]);
                    }
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
