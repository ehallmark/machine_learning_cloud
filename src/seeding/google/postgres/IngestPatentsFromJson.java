package seeding.google.postgres;

import models.assignee.normalization.name_correction.NormalizeAssignees;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.mongo.ingest.IngestJsonHelper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class IngestPatentsFromJson {

    private static final LocalDate twentyFiveYearsAgo = LocalDate.of(2018,4,7).minusYears(22);
    protected static final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
        String filingDate = (String)doc.get(SeedingConstants.FILING_DATE);
        if(filingDate==null||filingDate.length()!=10) return false;
        return LocalDate.parse(filingDate, DateTimeFormatter.ISO_DATE).isAfter(twentyFiveYearsAgo);
    };

    protected static final List<Function<Document,Void>> attributeFunctions = Arrays.asList(
            map -> {
                // handle publication numbers
                String publicationNumber = (String)map.get(SeedingConstants.PUBLICATION_NUMBER_GOOGLE);
                if(publicationNumber!=null) {
                    String[] parts = publicationNumber.split("-");
                    if(parts.length==3) {
                        if(parts[0].equals("US")&&parts[1].length()==10) {
                            // fix
                            parts[1]=parts[1].substring(0,4)+"0"+parts[1].substring(4);
                        }
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

    public static void main(String[] args) throws SQLException {
        final File dataDir = new File("/usb/data/google-big-query/patents/");

        String[] fields = new String[]{
                SeedingConstants.FULL_PUBLICATION_NUMBER,
                SeedingConstants.PUBLICATION_NUMBER,
                SeedingConstants.FULL_APPLICATION_NUMBER,
                SeedingConstants.APPLICATION_NUMBER,
                SeedingConstants.APPLICATION_NUMBER_FORMATTED,
                SeedingConstants.FILING_DATE,
                SeedingConstants.PUBLICATION_DATE,
                SeedingConstants.PRIORITY_DATE,
                SeedingConstants.COUNTRY_CODE,
                SeedingConstants.KIND_CODE,
                SeedingConstants.APPLICATION_KIND,
                SeedingConstants.FAMILY_ID,
                SeedingConstants.TITLE_LOCALIZED+"."+SeedingConstants.TEXT,
                SeedingConstants.TITLE_LOCALIZED+"."+SeedingConstants.LANGUAGE,
                SeedingConstants.ABSTRACT_LOCALIZED+"."+SeedingConstants.TEXT,
                SeedingConstants.ABSTRACT_LOCALIZED+"."+SeedingConstants.LANGUAGE,
                SeedingConstants.CLAIMS_LOCALIZED+"."+SeedingConstants.TEXT,
                SeedingConstants.CLAIMS_LOCALIZED+"."+SeedingConstants.LANGUAGE,
                SeedingConstants.DESCRIPTION_LOCALIZED+"."+SeedingConstants.TEXT,
                SeedingConstants.DESCRIPTION_LOCALIZED+"."+SeedingConstants.LANGUAGE,
                SeedingConstants.INVENTOR,
                SeedingConstants.ASSIGNEE,
                SeedingConstants.INVENTOR_HARMONIZED+"."+SeedingConstants.NAME,
                SeedingConstants.INVENTOR_HARMONIZED+"."+SeedingConstants.COUNTRY_CODE,
                SeedingConstants.ASSIGNEE_HARMONIZED+"."+SeedingConstants.NAME,
                SeedingConstants.ASSIGNEE_HARMONIZED+"."+SeedingConstants.COUNTRY_CODE,
                SeedingConstants.PRIORITY_CLAIM+"."+SeedingConstants.FULL_PUBLICATION_NUMBER,
                SeedingConstants.PRIORITY_CLAIM+"."+SeedingConstants.FULL_APPLICATION_NUMBER,
                SeedingConstants.PRIORITY_CLAIM+"."+SeedingConstants.FILING_DATE,
                SeedingConstants.CPC+"."+ SeedingConstants.CODE,
                SeedingConstants.CPC+"."+ SeedingConstants.INVENTIVE,
                SeedingConstants.CITATION+"."+SeedingConstants.FULL_PUBLICATION_NUMBER,
                SeedingConstants.CITATION+"."+SeedingConstants.FULL_APPLICATION_NUMBER,
                SeedingConstants.CITATION+"."+SeedingConstants.NPL_TEXT,
                SeedingConstants.CITATION+"."+SeedingConstants.TYPE,
                SeedingConstants.CITATION+"."+SeedingConstants.CATEGORY,
                SeedingConstants.CITATION+"."+SeedingConstants.FILING_DATE
        };

        Set<String> arrayFields = new HashSet<>();
        arrayFields.add(SeedingConstants.TITLE_LOCALIZED);
        arrayFields.add(SeedingConstants.DESCRIPTION_LOCALIZED);
        arrayFields.add(SeedingConstants.CLAIMS_LOCALIZED);
        arrayFields.add(SeedingConstants.INVENTOR);
        arrayFields.add(SeedingConstants.ASSIGNEE);
        arrayFields.add(SeedingConstants.ASSIGNEE_HARMONIZED);
        arrayFields.add(SeedingConstants.INVENTOR_HARMONIZED);
        arrayFields.add(SeedingConstants.PRIORITY_CLAIM);
        arrayFields.add(SeedingConstants.CPC);
        arrayFields.add(SeedingConstants.CITATION);

        Set<String> fieldsToCapitalize = new HashSet<>();
        fieldsToCapitalize.add(SeedingConstants.ASSIGNEE);
        fieldsToCapitalize.add(SeedingConstants.ASSIGNOR);
        fieldsToCapitalize.add(SeedingConstants.ASSIGNEE_HARMONIZED);
        fieldsToCapitalize.add(SeedingConstants.INVENTOR);
        fieldsToCapitalize.add(SeedingConstants.NAME);

        Set<String> booleanFields = new HashSet<>();
        booleanFields.add(SeedingConstants.CPC+"."+SeedingConstants.INVENTIVE);

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
            boolean isDate = childField.equals("date")||childField.endsWith("_date")||childField.endsWith("Date")||childField.contains("_date_");
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

        final String sql = "insert into patents_global (publication_number_full,publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,invention_title,invention_title_lang,abstract,abstract_lang,claims,claims_lang,description,description_lang,inventor,assignee,inventor_harmonized,inventor_harmonized_cc,assignee_harmonized,assignee_harmonized_cc,pc_publication_number_full,pc_application_number_full,pc_filing_date,code,inventive,cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date) values "+valueStr+" on conflict do nothing";

        Function<Document, List<Object>> transformer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
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
                    Object val;
                    final boolean capitalizeValues = (fieldsToCapitalize.contains(childField));
                    if(field.contains(".")) {
                        boolean isDate = field.equals("date")||field.endsWith("_date")||field.endsWith("Date")||field.contains("_date_");
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
                                } else if(capitalizeValues) {
                                    if(objs[j]!=null) {
                                        objs[j]=((String)objs[j]).toUpperCase();
                                    }
                                }
                            }
                            val = objs;
                        } else {
                            val = null;
                        }
                    } else {
                        val = doc.get(fields[i]);
                        if(capitalizeValues && val!=null) {
                            final boolean isArray = arrayFields.contains(parentField);
                            if(isArray) {
                                val = ((List<String>)val).stream().map(x->{
                                    if(x!=null) {
                                        return NormalizeAssignees.manualCleanse(x);
                                    } else return null;
                                }).collect(Collectors.toList());
                            } else {
                                val = NormalizeAssignees.manualCleanse((String)val);
                            }
                        }
                    }
                    if(i==7) { // priority date
                        if(val==null||val.equals("0")) {
                            // default to filing date
                            val = data.get(5);
                        }
                    }
                    data.add(val);
                }
                return data;
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
                return null;
            }
        };

        ExecutorService service = Executors.newFixedThreadPool(8);
        Stream.of(dataDir.listFiles()).forEach(file-> {
            service.execute(() -> {
                try (InputStream stream = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                    Connection conn = Database.newSeedConn();
                    PreparedStatement ps = conn.prepareStatement(sql);
                    DefaultApplier applier = new DefaultApplier(false, conn, fields);
                    QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);
                    IngestJsonHelper.streamJsonFile(stream, attributeFunctions).filter(map -> filterDocumentFunction.apply(map)).forEach(map -> {
                        List<Object> data = transformer.apply(new Document(map));
                        if(data!=null) {
                            try {
                                queryStream.ingest(data, null, ps);
                            } catch(Exception e2) {
                                e2.printStackTrace();
                                System.exit(1);
                            }
                        }
                    });
                    ps.close();
                    conn.commit();
                    conn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            });
        });

        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
