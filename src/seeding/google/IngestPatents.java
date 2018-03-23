package seeding.google;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import elasticsearch.MongoDBClient;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static seeding.google.IngestJsonHelper.ingestJsonDump;

public class IngestPatents {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "patents";

    public static void main(String[] args) {
        final String idField = "publication_number";
        final File dataDir = new File("/media/ehallmark/My Passport/data/google-big-query/patents/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);
        final int twentyFiveYearsAgo = LocalDate.now().getYear()*10000;
        final Function<Map<String,Object>,Boolean> filterDocumentFunction = doc -> {
            Integer filingDate = (Integer)doc.get("filing_date");
            if(filingDate==null||filingDate==0) return false;
            return filingDate >= twentyFiveYearsAgo;
        };
        
        ingestJsonDump(idField,dataDir,collection,true,filterDocumentFunction);
    }
}
