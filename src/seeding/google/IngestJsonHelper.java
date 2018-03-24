package seeding.google;

import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import org.nd4j.linalg.primitives.Pair;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IngestJsonHelper {
    private static final int batchSize = 1000;
    private static final int maximumStringLength = 100000;

    public static void ingestJsonDump(String idField, File dataDir, MongoCollection collection, boolean create, Function<Map<String,Object>,Boolean> filter, List<Function<Document,Void>> attributeFunctions) {
        final AtomicInteger counter = new AtomicInteger(0);
        final AtomicInteger idx = new AtomicInteger(0);

        System.out.println("Starting to ingest "+dataDir.getAbsolutePath()+"...");
        final Function<Map<String, Object>, WriteModel<Document>> mapToDocumentModelFunction = map -> {
            String id = (String) map.get(idField);
            if (id == null) return null;
            Document upsertDoc = new Document("$set", map);
            Document upsertQuery = new Document("_id", id);
            WriteModel<Document> model = new UpdateOneModel<>(upsertQuery, upsertDoc, new UpdateOptions().upsert(create));
            return model;
        };

        Stream.of(dataDir.listFiles()).parallel().forEach(file-> {
            final List<WriteModel<Document>> documentList = new ArrayList<>(batchSize);
            try(InputStream stream = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                IngestJsonHelper.streamJsonFile(stream,attributeFunctions).forEach(map->{
                    if(filter!=null&&!filter.apply(map)) {
                        return;
                    }

                    if(idx.getAndIncrement()%10000==9999) {
                        System.out.println("Completed: "+idx.get());
                    }
                    WriteModel<Document> model = mapToDocumentModelFunction.apply(map);
                    if(model!=null) {
                        documentList.add(model);
                    }
                    if(documentList.size()==batchSize) {
                        IngestJsonHelper.ingest(collection,new ArrayList<>(documentList),counter);
                        documentList.clear();
                    }
                });
                if(documentList.size()>0) {
                    IngestJsonHelper.ingest(collection,new ArrayList<>(documentList),counter);
                }
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });

        IngestJsonHelper.waitForCompletion(counter);
        System.out.println("Completed normally.");
    }

    public static void ingest(MongoCollection collection, List<WriteModel<Document>> updateBatch, AtomicInteger counter) {
        counter.getAndIncrement();
        collection.bulkWrite(updateBatch, new BulkWriteOptions().ordered(false), (v, t) -> {
            counter.getAndDecrement();
            if (t != null) {
                System.out.println("Failed in update: " + t.getMessage());
            }
        });
    }

    public static void waitForCompletion(AtomicInteger counter) {
        while(counter.get()>0) {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static List<Document> handleList(List<Document> list, int level, List<Function<Document,Void>> attributeFunctions) {
        return list.stream().map(d->handleMap(d,level,attributeFunctions)).collect(Collectors.toList());
    }

    private static Document handleMap(Document document, int level, List<Function<Document,Void>> attributeFunctions) {
        Document doc = new Document(document.entrySet().stream().map(e->{

            Object v = e.getValue();
            //System.out.println(String.join("", IntStream.range(0,level).mapToObj(i->" ").collect(Collectors.toList()))+e.getKey());
            if(v==null) {
                return new Pair<>(e.getKey(),v);
            } else if(v instanceof List) {
                List list = (List)v;
                if(list.isEmpty()) return new Pair<>(e.getKey(),list);
                Object i = list.get(0);
                if(i instanceof Document) {
                    return new Pair<>(e.getKey(),handleList((List<Document>)v,level+1,attributeFunctions));
                } else {
                    return new Pair<>(e.getKey(),list.stream().map(li->handleValue(e.getKey(),li)).collect(Collectors.toList()));
                }
            } else if (v instanceof Document) {
                return new Pair<>(e.getKey(),handleMap((Document) v,level+1, attributeFunctions));
            } else {
                return new Pair<>(e.getKey(),handleValue(e.getKey(),v));
            }
        }).filter(e->e.getSecond()!=null).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond())));
        // add other attributes
        attributeFunctions.forEach(function->{
            function.apply(doc);
        });

        //System.out.println();
        return doc;
    }

    private static Object handleValue(String k, Object v) {
        if(v!=null && v instanceof String) {
            if(((String) v).length()>maximumStringLength) {
                v = ((String) v).substring(0, maximumStringLength);
            }
            v = ((String) v).trim();
            if(((String) v).length()==0) {
                return null;
            }
            if(k.endsWith("_date")&&v.equals("0")) {

            }
        } else if(k.endsWith("_date") && v!=null&& v instanceof Integer) {
            if(v.equals(0)) {
                return null;
            }
        }
        return v;
    }


    public static Stream<Map<String,Object>> streamJsonFile(InputStream is, List<Function<Document,Void>> attributeFunctions) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        return reader.lines().map(line-> {
            Document map = Document.parse(line);
            return handleMap(map,1,attributeFunctions);
        });
    }

    // test
    public static void main(String[] args) throws Exception {
        File dir = new File("/media/ehallmark/My Passport/data/google-big-query/patents/");
        File file = dir.listFiles()[0];
        GzipCompressorInputStream gzip = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file)));
        streamJsonFile(gzip, Collections.emptyList()).forEach(map->{
            if(!map.containsKey("filing_date")||map.get("filing_date").toString().length()<2) {
                //System.out.println("Line: " + String.join("; ", map.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.toList())));
                //throw new RuntimeException("No filing date...");
            } else {
                LocalDate date = LocalDate.parse((String)map.get("filing_date"), DateTimeFormatter.BASIC_ISO_DATE);
                if(date.isAfter(LocalDate.now().minusYears(25))) {
                    System.out.println("Date: " + date);
                }
            }
        });
    }
}
